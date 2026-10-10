# Valid completed frames only. Duration includes missed-vsync delay.
# Nearest-rank percentiles of (FrameCompleted - IntendedVsync), in ms.
BEGIN { FS="," }
/^Flags,/ {
    for (i=1;i<=NF;i++) { name=$i; gsub(/\r/,"",name); col[name]=i }
    next
}
/^[0-9]+,/ {
    if (!col["Flags"] || !col["IntendedVsync"] || !col["FrameCompleted"]) next
    flags=$(col["Flags"])+0; start=$(col["IntendedVsync"])+0; end=$(col["FrameCompleted"])+0
    if (flags != 0 || start <= 0 || end <= start || end >= 9e18) next
    d=(end-start)/1000000
    # gfxinfo ring buffers are small; portable insertion sort avoids GNU-only asort.
    j=++n; while (j>1 && values[j-1]>d) { values[j]=values[j-1]; j-- }
    values[j]=d
}
END {
    if (!n) { print "0,NA,NA"; exit }
    p95=int(n*.95); if (p95<n*.95) p95++
    p99=int(n*.99); if (p99<n*.99) p99++
    printf "%d,%.3f,%.3f\n",n,values[p95],values[p99]
}
