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

    def test_uicons_vectors_and_reference_style(self):
        import xml.etree.ElementTree as ET
        provenance = load(ASSETS / 'main/assets/uicons-provenance.json')
        self.assertEqual('3.3.1', provenance['version'])
        self.assertEqual('Regular Rounded', provenance['style'])
        self.assertEqual('compass-alt', provenance['icons']['Explore']['glyph'])
        for icon in provenance['icons'].values():
            vector = ET.parse(ASSETS / ('main/res/drawable/' + icon['drawable'] + '.xml')).getroot()
            self.assertTrue(vector.find('path').attrib['{http://schemas.android.com/apk/res/android}pathData'])
        for path in (ASSETS / 'main/java/com/hydro/reedance/ui').glob('*.kt'):
            self.assertNotIn('Icons.Rounded', path.read_text())
        self.assertTrue((ASSETS / 'main/assets/Uicons-LICENSE.txt').is_file())

    def test_catalog_has_no_search_and_uses_lazy_offthread_covers(self):
        source = (ASSETS / 'main/java/com/hydro/reedance/ui/Catalog.kt').read_text()
        self.assertNotIn('OutlinedTextField', source)
        self.assertIn('LazyVerticalGrid', source)
        self.assertIn('withContext(Dispatchers.IO)', source)
        self.assertIn('🇬🇧', source)
        for name in ['vazirmatn.ttf','vazirmatn_bold.ttf','manrope_bold.ttf']:
            self.assertGreater((ASSETS / ('main/res/font/' + name)).stat().st_size, 10000)

    def test_only_one_splash_and_no_whole_page_transition(self):
        source = (ASSETS / 'main/java/com/hydro/reedance/ui/App.kt').read_text()
        self.assertNotIn('BrandSplash', source)
        self.assertNotIn('AnimatedContent(page', source)
        activity = (ASSETS / 'main/java/com/hydro/reedance/MainActivity.kt').read_text()
        self.assertEqual(1, activity.count('= installSplashScreen()'))
        self.assertIn('setKeepOnScreenCondition', activity)

    def test_navigation_filled_variants_are_distinct_and_valid(self):
        data = load(ASSETS / 'main/assets/uicons-provenance.json')['icons']
        for name in ['Home', 'Explore', 'Review', 'Library']:
            self.assertEqual('Solid Rounded', data[name+'Filled']['style'])
            normal = ASSETS / ('main/res/drawable/' + data[name]['drawable'] + '.xml')
            filled = ASSETS / ('main/res/drawable/' + data[name+'Filled']['drawable'] + '.xml')
            self.assertNotEqual(normal.read_bytes(), filled.read_bytes())

    def test_circle_flags_and_downloaded_filter(self):
        for code in ['gb','es','kr']:
            png = (ASSETS / ('main/res/drawable-nodpi/flag_' + code + '_circle.png')).read_bytes()
            self.assertEqual(b'\x89PNG\r\n\x1a\n', png[:8])
            self.assertEqual((192,192), struct.unpack('>II', png[16:24]))
            self.assertEqual(6, png[25])  # RGBA: transparent outside circular edge
        source = (ASSETS / 'main/java/com/hydro/reedance/ui/Catalog.kt').read_text()
        self.assertIn('3 -> s.s("id") in installedIds', source)
        self.assertIn('remember(catalog, language, access, installedIds)', source)
        self.assertIn('.animateItem(', source)
        self.assertNotIn('"Playable"', source)
        for path in (ASSETS / 'main/java/com/hydro/reedance/ui').glob('*.kt'):
            self.assertNotIn('Uicons by Flaticon', path.read_text())

    def test_navigation_is_capsule_transparent_and_observes_scroll(self):
        nav = (ASSETS / 'main/java/com/hydro/reedance/ui/FloatingNavigation.kt').read_text()
        self.assertIn('shape = CircleShape', nav)
        self.assertIn('shadowElevation = 2.dp', nav)
        self.assertIn('AnimatedVisibility(!compact', nav)
        self.assertIn('if(active) destination.filled else destination.outline', nav)
        self.assertIn('52.dp else 20.dp', nav)
        app = (ASSETS / 'main/java/com/hydro/reedance/ui/App.kt').read_text()
        self.assertIn('.nestedScroll(scrollConnection)', app)
        self.assertIn('window.isNavigationBarContrastEnforced = false', app)
        self.assertIn('android.graphics.Color.TRANSPARENT', app)

    def test_global_press_feedback_without_ripple(self):
        theme = (ASSETS / 'main/java/com/hydro/reedance/ui/Theme.kt').read_text()
        self.assertIn('LocalIndication provides rememberPressFeedback()', theme)
        self.assertIn('LocalRippleConfiguration provides null', theme)
        feedback = (ASSETS / 'main/java/com/hydro/reedance/ui/PressFeedback.kt').read_text()
        for token in ['IndicationNodeFactory','PressInteraction.Release','PressInteraction.Cancel','PressFeedback(!reduced)']:
            self.assertIn(token, feedback)
        import re
        for path in (ASSETS / 'main/java/com/hydro/reedance/ui').glob('*.kt'):
            if path.name != 'PressFeedback.kt':
                self.assertIsNone(re.search(r'\b(?:Button|TextButton|OutlinedButton|IconButton|FilledIconButton|RadioButton|Switch|FilterChip)\(', path.read_text()), str(path))
        nav = (ASSETS / 'main/java/com/hydro/reedance/ui/FloatingNavigation.kt').read_text()
        self.assertNotIn('secondaryContainer', nav)

    def test_overlay_navigation_and_splash_resources(self):
        import xml.etree.ElementTree as ET
        app = (ASSETS / 'main/java/com/hydro/reedance/ui/App.kt').read_text()
        self.assertIn('LocalOverlayBottomInset provides if(independent) 0.dp else padding.calculateBottomPadding()', app)
        self.assertNotIn('Box(Modifier.padding(padding)', app)
        for name in ['Catalog.kt','Library.kt','Profile.kt','Starter.kt']:
            self.assertIn('LocalOverlayBottomInset.current', (ASSETS / ('main/java/com/hydro/reedance/ui/' + name)).read_text())
        styles = ET.parse(ASSETS / 'main/res/values/styles.xml')
        items = {x.attrib['name']: x.text for x in styles.findall(".//style[@name='Theme.Reedance.Starting']/item")}
        self.assertEqual('#002AFC', items['windowSplashScreenBackground'])
        self.assertEqual('@drawable/reedance_splash_icon', items['windowSplashScreenAnimatedIcon'])
        png = (ASSETS / 'main/res/drawable-nodpi/reedance_splash_icon.png').read_bytes()
        self.assertEqual((512,512), struct.unpack('>II', png[16:24]))
        self.assertEqual(6, png[25])

if __name__ == '__main__': unittest.main(verbosity=2)
