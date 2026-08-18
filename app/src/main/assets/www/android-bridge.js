// VyaparDesk Android Bridge — enhances PWA inside native WebView
(function() {
  if (!window.Android || window.__vyaparAndroidPatched) return;
  window.__vyaparAndroidPatched = true;
  console.log('[VyaparDesk] Android bridge active');

  // Patch anchor click to handle blob: downloads in WebView
  const origClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function() {
    const href = this.href || this.getAttribute('href') || '';
    const download = this.download || this.getAttribute('download') || '';
    if (href.startsWith('blob:') && download) {
      // Try to retrieve blob handling via fetch (blob URLs are fetchable)
      const url = href;
      console.log('[VyaparDesk] Intercept blob download:', download, url);
      // Use fetch to get blob then convert to base64 and send to Android
      fetch(url).then(r => r.blob()).then(blob => {
        const reader = new FileReader();
        reader.onload = function() {
          const base64 = reader.result.split(',')[1];
          try {
            if (window.Android && window.Android.saveBase64File) {
              window.Android.saveBase64File(base64, download, blob.type || 'application/octet-stream');
            } else if (window.Android && window.Android.showToast) {
              window.Android.showToast('Downloading ' + download);
            }
          } catch (e) {
            console.error('Android save failed', e);
          }
          // revoke after
          try { URL.revokeObjectURL(url); } catch(e){}
        };
        reader.readAsDataURL(blob);
      }).catch(err => {
        console.error('blob fetch failed', err);
        // fallback to original click
        origClick.call(this);
      });
      return;
    }
    return origClick.call(this);
  };

  // Also patch createElement('a') download flow: if JS creates anchor and sets href to blob then clicks,
  // the above prototype will handle it. No need to patch URL.createObjectURL.

  // Inject style to hide scrollbars quirks and hide PWA prompt if any
  const style = document.createElement('style');
  style.textContent = '::-webkit-scrollbar{width:0}';
  document.head && document.head.appendChild(style);

  // Expose helper for manual share
  window.vyaparShareFile = function(base64, filename, mime) {
    if (window.Android && window.Android.shareBase64File) {
      window.Android.shareBase64File(base64, filename, mime);
    }
  };

  console.log('[VyaparDesk] Android bridge ready');
})();
