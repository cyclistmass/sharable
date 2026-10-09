# Android gfxinfo summary -> CSV. Missing metrics remain NA, never become zero.
BEGIN { total=janky=pct=p50=p90=p95=slow="NA" }
/Total frames rendered:/ { total=$NF }
/Janky frames:/ && !/legacy/ {
    janky=$3
    pct=$4; gsub(/[()%]/,"",pct)
}
/50th percentile:/ { p50=$NF; sub(/ms$/,"",p50) }
/90th percentile:/ { p90=$NF; sub(/ms$/,"",p90) }
/95th percentile:/ { p95=$NF; sub(/ms$/,"",p95) }
/Number Slow UI thread:/ { slow=$NF }
END { printf "%s,%s,%s,%s,%s,%s,%s\n",total,janky,pct,p50,p90,p95,slow }
