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
        self.assertIn('remember(catalog, targetLanguage, home, access, installedIds)', source)
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

    def test_long_learning_screens_are_lazy_and_nav_slot_is_stable(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        for name in ['Song.kt', 'Starter.kt']:
            source = (ui / name).read_text()
            self.assertIn('LazyColumn(', source)
            self.assertNotIn('.verticalScroll(', source)
            self.assertIn('contentType =', source)
        song = (ui / 'Song.kt').read_text()
        self.assertIn('rememberLazyListState()', song)
        self.assertIn('scroll.firstVisibleItemIndex > 0', song)
        self.assertIn('items(lines.take(access.limit("lyrics")), key =', song)
        nav = (ui / 'FloatingNavigation.kt').read_text()
        self.assertIn('heightIn(min = slotHeight)', nav)
        self.assertIn('labelSmall.lineHeight.toDp()', nav)
        self.assertNotIn('if(compact)', nav[nav.index('val slotHeight'):nav.index('// Keep')])

    def test_centered_brand_and_persistent_song_language_picker(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        header = (ui / 'MainHeader.kt').read_text()
        for token in ['ModalBottomSheet', 'Modifier.weight(1f)', 'slideInVertically', 'slideOutVertically', 'repeatOnLifecycle(Lifecycle.State.STARTED)', 'if(!reduced)', 'clearAndSetSemantics']:
            self.assertIn(token, header)
        for code in ['en', 'es', 'ko']:
            self.assertIn('SongLanguage("' + code + '"', header)
        app = (ui / 'App.kt').read_text()
        self.assertIn('MainHeader(settings.targetLanguage, model::targetLanguage', app)
        self.assertNotIn('LocalFaValue', app)
        self.assertNotIn('model.language(if', app)
        catalog = (ui / 'Catalog.kt').read_text()
        self.assertIn('s.s("language") == targetLanguage', catalog)
        self.assertNotIn('flag = R.drawable.flag_', catalog)
        self.assertNotIn('var language', catalog)
        settings = (ASSETS / 'main/java/com/hydro/reedance/data/Settings.kt').read_text()
        self.assertIn('val targetLanguage: String = "en"', settings)
        self.assertIn('stringPreferencesKey("target_language")', settings)
        self.assertIn('ChoiceRow(listOf("English", "فارسی")', (ui / 'Profile.kt').read_text())

    def test_home_keeps_original_banner_and_starter_above_latest_songs(self):
        source = (ASSETS / 'main/java/com/hydro/reedance/ui/Catalog.kt').read_text()
        home = source[source.index('        if(home) {'):source.index('        item(key = "heading"')]
        self.assertIn('item(key = "hero"', home)
        self.assertIn('item(key = "starter"', home)
        self.assertLess(home.index('item(key = "hero"'), home.index('item(key = "starter"'))
        self.assertIn('openSong("midnight-avenue")', home)
        self.assertIn('PressTextButton(starter)', home)
        self.assertNotIn('PillTabs', home)
        self.assertIn('if(!home) item(key = "filters"', source)
        self.assertIn('if(home) latestSongs(it)', source)

    def test_home_latest_and_premium_learning_preview(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        catalog = (ui / 'Catalog.kt').read_text()
        self.assertIn('if(!home) item(key = "filters"', catalog)
        self.assertIn('when(if(home) 0 else access)', catalog)
        self.assertIn('if(home) latestSongs(it)', catalog)
        self.assertNotIn('UiIcons.Lock', catalog)
        self.assertIn('if(song.s("access") == "paid") Surface', catalog)
        song = (ui / 'Song.kt').read_text()
        for section in ['lyrics', 'grammar', 'vocabulary']:
            self.assertIn('take(access.limit("' + section + '"))', song)
        self.assertIn('take(access.limit(type))', song)
        self.assertIn('if(access.preview) item(key = "subscription")', song)
        self.assertIn('if(!access.preview) items(data.a("vocabularyTables")', song)
        premium = (ui / 'Premium.kt').read_text()
        self.assertIn('Modifier.blur(7.dp)', premium)
        self.assertNotIn('JsonObject', premium)  # never compose hidden lesson text/actions
        policy = (ASSETS / 'main/java/com/hydro/reedance/domain/ContentAccess.kt').read_text()
        self.assertIn('"vocabulary" -> 5', policy)
        self.assertIn('"lyrics" -> 3', policy)
        self.assertIn('else -> 2', policy)
        self.assertIn('.take(40)', policy)

    def test_headers_have_consistent_opaque_background(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        theme = (ui / 'Theme.kt').read_text()
        header = theme[theme.index('@Composable internal fun HeaderSurface'):]
        for token in ['color = MaterialTheme.colorScheme.background', 'tonalElevation = 0.dp', 'shadowElevation = 0.dp']:
            self.assertIn(token, header)
        self.assertIn('if(page != "song") HeaderSurface {', (ui / 'App.kt').read_text())
        song = (ui / 'Song.kt').read_text()
        self.assertIn('HeaderSurface {', song)
        self.assertNotIn('shadowElevation = if(collapsed)', song)

    def test_coroutine_startup_and_observer_driven_playback(self):
        source = (ASSETS / 'main/java/com/hydro/reedance/data/AppModel.kt').read_text()
        self.assertIn('coroutineScope {', source)
        self.assertIn('async { content.catalog() }', source)
        self.assertIn('async { content.starters() }', source)
        self.assertIn('combine(playing, position.subscriptionCount)', source)
        self.assertIn('delay(5_000); checkpoint', source)
        self.assertIn('Channel.CONFLATED', source)
        self.assertNotIn('delay(700)', source)
        self.assertIn('catch(cancelled: CancellationException) { throw cancelled }', source)
        self.assertIn('private var speech: Speech? = null', source)
        self.assertIn('private var controllerFuture: ListenableFuture<MediaController>? = null', source)
        song = (ASSETS / 'main/java/com/hydro/reedance/ui/Song.kt').read_text()
        song_screen = song[:song.index('@Composable private fun PlayerPanel')]
        self.assertNotIn('model.position.collectAsStateWithLifecycle()', song_screen)
        self.assertIn('model.activeLineId.collectAsStateWithLifecycle()', song_screen)

    def test_repository_keeps_file_and_cpu_work_off_main(self):
        source = (ASSETS / 'main/java/com/hydro/reedance/data/LocalContentRepository.kt').read_text()
        self.assertIn('Dispatchers.IO', source)
        self.assertIn('Dispatchers.Default', source)
        self.assertIn('withContext(cpu)', source)
        self.assertIn('withContext(io)', source)
        self.assertIn('filesMutex.withLock', source)
        self.assertIn('ensureActive()', source)
        self.assertNotIn('.readBytes()', source)
        self.assertIn('withContext(NonCancellable + io)', source)
        speech = (ASSETS / 'main/java/com/hydro/reedance/media/Speech.kt').read_text()
        self.assertIn('suspend fun say', speech)
        self.assertIn('initialized.await()', speech)
        self.assertIn('withContext(Dispatchers.IO)', speech)
        quiz = (ASSETS / 'main/java/com/hydro/reedance/ui/Quiz.kt').read_text()
        self.assertIn('withContext(Dispatchers.Default)', quiz)
        self.assertIn('model.dueReviewCards()', quiz)

if __name__ == '__main__': unittest.main(verbosity=2)
