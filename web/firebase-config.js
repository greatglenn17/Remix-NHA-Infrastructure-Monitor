// Web app config of the live project nha-monitor-bulacan-2026. These values are public by design
// (the security rules protect the data). From the "NHA Monitor Web" app:
// firebase apps:sdkconfig WEB 1:1091951535339:web:9f23ffea71ebca0f868aae
export const firebaseConfig = {
  apiKey: 'AIzaSyDKhRERL2ClvmmYwbaZyQeiJXvksNL756M',
  authDomain: 'nha-monitor-bulacan-2026.firebaseapp.com',
  projectId: 'nha-monitor-bulacan-2026',
  storageBucket: 'nha-monitor-bulacan-2026.firebasestorage.app',
  messagingSenderId: '1091951535339',
  appId: '1:1091951535339:web:9f23ffea71ebca0f868aae',
};

// Copy to Google Drive: the OAuth client ID (Web application) from Google Cloud > APIs & Services > Credentials, also
// public by design ("NHA Monitor Web", made 2026-11-14; the consent screen is in Testing, so only its listed test
// users can sign in). Empty = the copy says it is not set up. The steps are in README.md, "Copy to Google Drive".
export const driveClientId = '1091951535339-srh1c025vbocfenfu3p6fe4j1jcc9asi.apps.googleusercontent.com';
