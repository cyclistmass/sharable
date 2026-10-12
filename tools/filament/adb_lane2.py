#!/usr/bin/env python3
"""Limited ADB smart-socket client for lane 2 when Windows adb profile lookup is denied.

Uses the existing server; never starts/stops it or selects another device. No third-party dependencies.
"""
import os
import pathlib
import shlex
import socket
import struct
import sys
import time

SERIAL = 'emulator-5556'


def exact(sock, size):
    data = bytearray()
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            raise RuntimeError('ADB closed connection')
        data.extend(chunk)
    return bytes(data)


def request(sock, command):
    data = command.encode()
    sock.sendall(f'{len(data):04x}'.encode() + data)
    status = exact(sock, 4)
    if status != b'OKAY':
        size = int(exact(sock, 4), 16)
        raise RuntimeError(exact(sock, size).decode(errors='replace'))


def connect(command):
    sock = socket.create_connection(('127.0.0.1', 5037), timeout=10)
    if command.startswith('host-serial:'):
        request(sock, command)
    else:
        request(sock, f'host:transport:{SERIAL}')
        request(sock, command)
    return sock


def stream(command):
    with connect(command) as sock:
        result = bytearray()
        while True:
            chunk = sock.recv(65536)
            if not chunk:
                return bytes(result)
            result.extend(chunk)


def shell(args):
    command = args[0] if len(args) == 1 else shlex.join(args)
    return stream('shell:' + command)


def push(local, remote):
    data = pathlib.Path(local).read_bytes()
    with connect('sync:') as sock:
        target = f'{remote},33206'.encode()
        sock.sendall(b'SEND' + struct.pack('<I', len(target)) + target)
        for start in range(0, len(data), 65536):
            chunk = data[start:start+65536]
            sock.sendall(b'DATA' + struct.pack('<I', len(chunk)) + chunk)
        sock.sendall(b'DONE' + struct.pack('<I', int(time.time())))
        status, length = struct.unpack('<4sI', exact(sock, 8))
        if status != b'OKAY':
            raise RuntimeError(exact(sock, length).decode())


def main(args):
    if os.getenv('ANDROID_SERIAL', SERIAL) != SERIAL:
        raise RuntimeError('This client only permits emulator lane 2')
    if args[:1] == ['-s']:
        if args[1] != SERIAL:
            raise RuntimeError('This client only permits emulator lane 2')
        args = args[2:]
    command, *rest = args
    if command == 'shell':
        sys.stdout.buffer.write(shell(rest))
    elif command == 'exec-out':
        sys.stdout.buffer.write(stream('exec:' + shlex.join(rest)))
    elif command == 'logcat':
        sys.stdout.buffer.write(shell(['logcat', *rest]))
    elif command == 'get-state':
        with connect(f'host-serial:{SERIAL}:get-state') as sock:
            size = int(exact(sock, 4), 16)
            print(exact(sock, size).decode())
    elif command == 'wait-for-device':
        deadline = time.monotonic() + 60
        while True:
            try:
                with connect('shell:true'):
                    break
            except (OSError, RuntimeError):
                if time.monotonic() > deadline:
                    raise
                time.sleep(1)
    elif command == 'install':
        path = rest[-1]
        remote = '/data/local/tmp/wgbg-filament-spike.apk'
        push(path, remote)
        result = shell(['pm', 'install', *rest[:-1], remote])
        sys.stdout.buffer.write(result)
        shell(['rm', remote])
        if b'Success' not in result:
            raise RuntimeError('Install failed')
    elif command == 'push':
        push(*rest)
    elif command == 'pull':
        pathlib.Path(rest[1]).write_bytes(stream('exec:cat ' + shlex.quote(rest[0])))
    else:
        raise RuntimeError(f'Unsupported command: {command}')


if __name__ == '__main__':
    try:
        main(sys.argv[1:])
    except Exception as error:
        print(f'lane 2 ADB: {error}', file=sys.stderr)
        sys.exit(1)
