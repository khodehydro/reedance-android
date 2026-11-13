"""Host-side asset/schema checks; NOT a substitute for compiling or running Kotlin."""
import hashlib
import json
import pathlib
import unittest
import wave
import zipfile
import io
import base64
import struct
from jsonschema import Draft202012Validator
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ROOT = pathlib.Path(__file__).resolve().parents[1]
FIX = ROOT / 'contracts/v1/fixtures'
ASSETS = ROOT / 'android/app/src'
def load(p): return json.loads(p.read_text())
def b64(s): return base64.urlsafe_b64decode(s + '=' * (-len(s) % 4))

class AssetTests(unittest.TestCase):
    def test_lesson_matches_unchanged_v1_schema(self):
        Draft202012Validator(load(ROOT / 'contracts/v1/lesson.schema.json')).validate(load(ASSETS / 'debug/assets/demo_lesson.json'))

    def test_bootstrap_has_no_invented_urls(self):
        catalog = load(ASSETS / 'main/assets/bootstrap_catalog.json')
        Draft202012Validator(load(ROOT / 'contracts/v1/bootstrap-catalog.schema.json')).validate(catalog)
        self.assertEqual([], catalog['songs'])

    def test_audio_matches_lesson_and_markers(self):
        lesson = load(ASSETS / 'debug/assets/demo_lesson.json')
        audio = ASSETS / 'debug/assets/midnight-avenue-demo.wav'
        self.assertEqual(hashlib.sha256(audio.read_bytes()).hexdigest(), lesson['media']['audio']['sha256'])
        with wave.open(str(audio)) as w:
            self.assertLessEqual(abs(w.getnframes()/w.getframerate()*1000-lesson['media']['audio']['durationMs']), 1000)
        marks = [l['startMs'] for l in lesson['lyrics']]
        self.assertEqual(sorted(set(marks)), marks)
        self.assertTrue(all(0 <= m < 64000 for m in marks))

    def test_starter_paths_and_tables(self):
        paths = load(ASSETS / 'main/assets/starter.json')
        self.assertEqual({'en', 'es', 'ko'}, set(paths))
        def tables(x):
            if isinstance(x, dict):
                if 'columns' in x and 'rows' in x:
                    self.assertTrue(x['columns'] and x['rows'])
                    self.assertTrue(all(len(r) == len(x['columns']) for r in x['rows']))
                for v in x.values(): tables(v)
            elif isinstance(x, list):
                for v in x: tables(v)
        tables(paths)
        for course in paths.values():
            for key in ['alphabetGroups', 'sounds', 'grammar', 'words', 'practice']: self.assertTrue(course[key])

    def test_quiz_references_and_four_formats(self):
        lesson = load(ASSETS / 'debug/assets/demo_lesson.json')
        words = {w['lexemeId'] for w in lesson['vocabulary']}
        self.assertEqual({'choice','match','order','typing'}, {q['type'] for q in lesson['questions']})
        for q in lesson['questions']:
            self.assertTrue(set(q.get('relatedLexemeIds', [])) <= words)
            if q['type'] == 'match': self.assertTrue({p['lexemeId'] for p in q['pairs']} <= words)
            if q['type'] == 'order': self.assertEqual({p['id'] for p in q['chunks']}, set(q['presentationOrder']))
            if q['type'] == 'choice': self.assertIn(q['correctId'], {o['id'] for o in q['options']})
        for line in lesson['lyrics']:
            self.assertTrue({t['lexemeId'] for t in line['tokens'] if 'lexemeId' in t} <= words)

    def test_upstream_fixture_signature_and_authenticated_ciphertext(self):
        keys, envelope = load(FIX/'keys.test.json'), load(FIX/'latest.json')
        payload = b64(envelope['payload'])
        message = b'REEDANCE-MANIFEST-V1\n'+envelope['signatureKeyId'].encode()+b'\n'+payload
        Ed25519PublicKey.from_public_bytes(b64(keys['ed25519PublicKeyBase64url'])).verify(b64(envelope['signature']), message)
        manifest = json.loads(payload)
        package = manifest['package']
        pack = (FIX/package['fileName']).read_bytes()
        self.assertEqual(package['sha256'], hashlib.sha256(pack).hexdigest())
        length = pack[8]
        header = pack[:29+length]
        self.assertEqual(b'RDPACK1\0', pack[:8])
        self.assertEqual(len(pack), 45+length+struct.unpack('>Q',pack[21+length:29+length])[0])
        nonce = pack[9+length:21+length]
        plain = AESGCM(b64(keys['aes256KeyBase64url'])).decrypt(nonce,pack[29+length:],header)
        with zipfile.ZipFile(io.BytesIO(plain)) as z:
            self.assertEqual((FIX/'lesson.json').read_bytes(), z.read('lesson.json'))
            self.assertEqual((FIX/'audio.wav').read_bytes(), z.read('audio.wav'))

    def test_release_assets_do_not_contain_test_keys_or_demos(self):
        main = ROOT/'android/app/src/main'
        for p in main.rglob('*'):
            if p.is_file():
                self.assertNotIn(b'aes256KeyBase64url', p.read_bytes(), str(p))
                self.assertNotIn(b'enc-test-v1', p.read_bytes(), str(p))
        self.assertFalse((main/'assets/demo_lesson.json').exists())

if __name__ == '__main__': unittest.main(verbosity=2)
