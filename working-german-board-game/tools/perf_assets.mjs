// Offline candidate resizes only. No dice/button assets, no replacement of originals.
// Needs ffmpeg + ffprobe on PATH. Run from repository root.
import { execFileSync } from 'node:child_process';
import { mkdirSync, statSync, writeFileSync } from 'node:fs';
const root = 'app/src/main/res/drawable-nodpi';
const output = 'perf-patches/assets';
mkdirSync(output, { recursive: true });
const rows = [];
for (const [name, width] of [['cliff_top_1',384], ['cliff_top_2',384], ['cliff_top_3',384], ['cliff_straight',512]]) {
  const src = `${root}/${name}.webp`;
  const candidate = `${output}/${name}_w${width}.webp`;
  const probe = path => JSON.parse(execFileSync('ffprobe', ['-v','error','-show_entries','stream=width,height','-of','json',path], {encoding:'utf8'})).streams[0];
  const before = probe(src);
  execFileSync('ffmpeg', ['-v','error','-y','-i',src,'-vf',`scale=${width}:-1:flags=lanczos`,'-c:v','libwebp','-lossless','1','-pix_fmt','bgra',candidate]);
  const after = probe(candidate);
  // Compare decoded pixels against exactly the same reference resample, including alpha.
  const raw = args => execFileSync('ffmpeg', ['-v','error',...args,'-pix_fmt','rgba','-f','rawvideo','-'], {maxBuffer:32*1024*1024});
  const reference = raw(['-i',src,'-vf',`scale=${width}:-1:flags=lanczos`]);
  const decoded = raw(['-i',candidate]);
  // Transparent RGB may be discarded by WebP, so compare visible, premultiplied pixels.
  let maxVisibleError = 0;
  for (let i=0; i<reference.length; i+=4) {
    maxVisibleError = Math.max(maxVisibleError, Math.abs(reference[i+3]-decoded[i+3]));
    for (let c=0;c<3;c++) maxVisibleError = Math.max(maxVisibleError, Math.abs(reference[i+c]*reference[i+3]/255-decoded[i+c]*decoded[i+3]/255));
  }
  if (maxVisibleError > 0) throw new Error(`${name}: visible pixel mismatch ${maxVisibleError}`);
  // Palette: original rendered at candidate size on left; decoded candidate on right.
  execFileSync('ffmpeg', ['-v','error','-y','-i',src,'-i',candidate,'-filter_complex',`[0:v]scale=${width}:-1:flags=lanczos,format=rgba[a];[1:v]format=rgba[b];[a][b]hstack=inputs=2`,'-frames:v','1',`${output}/${name}_palette.png`]);
  rows.push({name, original:{...before,bytes:statSync(src).size,rgbaBytes:before.width*before.height*4}, candidate:{...after,bytes:statSync(candidate).size,rgbaBytes:after.width*after.height*4},maxVisibleError});
}
writeFileSync(`${output}/measurements.json`, JSON.stringify(rows,null,2)+'\n');
console.log(JSON.stringify(rows,null,2));
