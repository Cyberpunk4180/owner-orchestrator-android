"""No-SDK repository-level checks. Not a substitute for Android compilation/device tests."""
from pathlib import Path
p=Path(__file__).resolve().parents[1]
man=(p/'app/src/main/AndroidManifest.xml').read_text()
code=(p/'app/src/main/java/org/owner/orchestrator/MainActivity.java').read_text()
api=(p/'app/src/main/java/org/owner/orchestrator/OrchestratorApi.java').read_text()
secure=(p/'app/src/main/java/org/owner/orchestrator/SecureStore.java').read_text()
assert 'android:usesCleartextTraffic="false"' in man
assert 'android:allowBackup="false"' in man
assert 'https://chatgpt.com/' in code
assert 'new Intent(Intent.ACTION_VIEW' in code
assert '/v1/jobs' in code and '/cancel' in code
assert 'HttpsURLConnection' in api and 'setInstanceFollowRedirects(false)' in api
assert 'AndroidKeyStore' in secure and 'AES/GCM/NoPadding' in secure
assert 'setBlockModes(KeyProperties.BLOCK_MODE_GCM)' in secure
assert 'onStop()' in code and 'main.removeCallbacks(periodic)' in code
print('PASS: 9 static structure/security checks')
