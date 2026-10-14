/*
 * Deletes EVERY scrobble from your Last.fm profile. Irreversible.
 * Runs inside your logged-in browser tab, so it needs nothing but a phone.
 *
 * Android setup (Chrome or Firefox):
 *  1. Bookmark any page, name it "unscrobble".
 *  2. Edit the bookmark: set the URL to `javascript:` followed by this whole file
 *     (type "javascript:" by hand, browsers strip it when pasted; then paste the rest).
 *  3. Open https://www.last.fm/user/YOURNAME while logged in.
 *  4. Type "unscrobble" in the address bar and tap the bookmark suggestion.
 *
 * Relies on Last.fm's web delete form (no public API for deletes) - UNVERIFIED against
 * the current site markup; if Last.fm changes it, the script stops with a message.
 */
(async () => {
  const user = (location.pathname.match(/^\/user\/([^/]+)/) || [])[1];
  if (!user) return alert('Open your own Last.fm profile page first (last.fm/user/YOURNAME).');
  if (prompt(`This permanently deletes ALL scrobbles of "${user}".\nType the username to confirm:`) !== user) return;

  const box = Object.assign(document.createElement('div'), {});
  box.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:99999;background:#b90000;color:#fff;padding:10px;font:16px sans-serif';
  document.body.append(box);
  const sleep = ms => new Promise(r => setTimeout(r, ms));
  let deleted = 0, fails = 0;

  while (fails < 5) {
    // Deleting shifts everything up, so page 1 is always the next batch.
    const res = await fetch(`/user/${user}/library?page=1`, { credentials: 'same-origin' });
    if (!res.ok) { fails++; await sleep(5000); continue; }
    const doc = new DOMParser().parseFromString(await res.text(), 'text/html');
    const forms = [...doc.querySelectorAll('form[action$="/library/delete"]')];
    if (!forms.length) break; // nothing deletable left (a "now playing" row has no form)

    for (const f of forms) {
      const r = await fetch(f.getAttribute('action'), {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'X-Requested-With': 'XMLHttpRequest' },
        body: new URLSearchParams(new FormData(f)),
      });
      if (r.ok) { deleted++; fails = 0; } else { fails++; await sleep(5000); }
      box.textContent = `Deleted ${deleted} scrobbles... keep this tab open`;
      await sleep(300); // gentle pacing to avoid rate limits
    }
  }
  box.textContent = fails >= 5
    ? `Stopped after repeated errors (logged out or rate-limited?). Deleted ${deleted}.`
    : `Done. Deleted ${deleted} scrobbles.`;
})();
