# Store graphics sources

Rendered with headless Chrome so they use the app's own fonts (`app/src/main/res/font`).

```sh
CH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
"$CH" --headless=new --hide-scrollbars --allow-file-access-from-files --force-device-scale-factor=1 \
  --window-size=512,512 --screenshot=../icon-512.png "file://$PWD/icon.html"
"$CH" --headless=new --hide-scrollbars --allow-file-access-from-files --force-device-scale-factor=1 \
  --window-size=1024,500 --screenshot=../feature-graphic-1024x500.png "file://$PWD/feature.html"
```

Screenshots: put a raw phone screenshot in `raw/<name>.png`, then render `frame.html` at 1080 × 1920 with
`?img=<name>&bg=<colour>&t=<title>&s=<subtitle>` (URL-encoded).
