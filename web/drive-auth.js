// The Google sign-in window for Copy to Google Drive comes back here with its answer after the #. Pass it to the
// NHA Monitor page that is waiting (app.js driveToken), take it out of the address, and close.
new BroadcastChannel('nha-drive').postMessage(location.hash.slice(1));
history.replaceState(null, '', location.pathname);
setTimeout(() => window.close(), 300);
