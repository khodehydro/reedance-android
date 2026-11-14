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

    def test_catalog_explore_search_and_lazy_offthread_covers(self):
        source = (ASSETS / 'main/java/com/hydro/reedance/ui/Catalog.kt').read_text()
        self.assertIn('if(!home) item(key = "filters"', source)
        self.assertIn('OutlinedTextField(value = query', source)
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
        self.assertIn('remember(catalog, targetLanguage, home, access, installedIds, query)', source)
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
        for token in ['ReedanceBottomSheet', 'Modifier.weight(1f)', 'slideInVertically', 'slideOutVertically', 'repeatOnLifecycle(Lifecycle.State.STARTED)', 'if(!reduced && !paused)', 'clearAndSetSemantics']:
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
        self.assertIn('HomePager(model, explore)', home)
        self.assertIn('PressButton(explore,', (ASSETS / 'main/java/com/hydro/reedance/ui/BannerMessages.kt').read_text())
        self.assertIn('PressButton(starter,', home)
        self.assertNotIn('PillTabs', home)
        self.assertIn('if(!home) item(key = "filters"', source)
        self.assertIn('if(home) latestSongs(it)', source)

    def test_premium_crown_is_filled_inline_without_background(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        source = (ui / 'Components.kt').read_text()
        title = source[source.index('@Composable fun SongTitle'):]
        self.assertIn('UiIcons.CrownFilled', title)
        self.assertIn('style.fontSize.toDp() * .9f', title)
        self.assertIn('if(premium)', title)
        self.assertLess(title.index('if(premium) AppIcon'), title.index('Text(title,'))
        self.assertNotIn('Surface(', title)
        self.assertNotIn('.background(', title)
        tile = (ui / 'Catalog.kt').read_text().split('@Composable private fun SongTile')[1]
        self.assertNotIn('Alignment.TopEnd', tile)
        self.assertIn('SongTitle(', tile)
        import xml.etree.ElementTree as ET
        normal = (ASSETS / 'main/res/drawable/uicon_crown.xml').read_bytes()
        filled = (ASSETS / 'main/res/drawable/uicon_crown_filled.xml').read_bytes()
        self.assertNotEqual(normal, filled)
        ET.fromstring(filled)

    def test_home_latest_and_premium_learning_preview(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        catalog = (ui / 'Catalog.kt').read_text()
        self.assertIn('if(!home) item(key = "filters"', catalog)
        self.assertIn('when(if(home) 0 else access)', catalog)
        self.assertIn('if(home) latestSongs(it)', catalog)
        self.assertNotIn('UiIcons.Lock', catalog)
        self.assertIn('SongTitle(song.s("title"), premium = song.s("access") == "paid")', catalog)
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

    def test_local_reminders_permissions_search_and_hearts(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        catalog = (ui / 'Catalog.kt').read_text()
        search = catalog[catalog.index('if(!home) item(key = "filters"'):catalog.index('items(filtered,')]
        self.assertIn('OutlinedTextField(value = query', search)
        self.assertIn('home || query.isBlank()', catalog)
        for name in ['Catalog.kt', 'Profile.kt']:
            self.assertIn('DeveloperCredit()', (ui / name).read_text())
        hearts = (ui / 'Hearts.kt').read_text()
        for token in ['positionInRoot()', 'Canvas(Modifier.matchParentSize())', 'origin.y - t *', 'job?.cancel()', 'if(reduced)']:
            self.assertIn(token, hearts)
        app = (ui / 'App.kt').read_text()
        self.assertIn('HeartOverlay {', app)
        self.assertIn('!settings.permissionsSetup -> PermissionSetup(model)', app)
        manifest = (ASSETS / 'main/AndroidManifest.xml').read_text()
        self.assertIn('android.permission.POST_NOTIFICATIONS', manifest)
        for permission in ['WRITE_EXTERNAL_STORAGE', 'READ_EXTERNAL_STORAGE', 'MANAGE_EXTERNAL_STORAGE', 'SCHEDULE_EXACT_ALARM']:
            self.assertNotIn(permission, manifest)
        worker = (ASSETS / 'main/java/com/hydro/reedance/notifications/LearningReminders.kt').read_text()
        for token in ['enqueueUniqueWork', 'ExistingWorkPolicy.APPEND_OR_REPLACE', 'cancelUniqueWork', 'ReminderPolicy.eligible', 'dueCount(now)', 'stepCount()', 'FLAG_IMMUTABLE', 'catch(_: SecurityException)']:
            self.assertIn(token, worker)
        self.assertNotIn('http', worker)
        self.assertIn('ReminderControls(model)', (ui / 'Profile.kt').read_text())
        self.assertIn('OneTimeWorkRequestBuilder<LearningReminderWorker>()', worker)
        self.assertNotIn('PeriodicWorkRequestBuilder<', worker)
        self.assertNotIn('12 hours', (ui / 'PermissionSetup.kt').read_text())
        self.assertNotIn('۱۲ ساعت', (ui / 'PermissionSetup.kt').read_text())
        setup = (ui / 'PermissionSetup.kt').read_text()
        self.assertNotIn('Downloads and offline storage', setup)
        self.assertIn('Panel { ReminderControls(model) }', setup)

    def test_supplied_header_and_notification_icons(self):
        header = (ASSETS / 'main/java/com/hydro/reedance/ui/MainHeader.kt').read_text()
        self.assertIn('R.drawable.reedance_header', header)
        self.assertNotIn('R.drawable.reedance_logo', header)
        for name in ['reedance_header', 'ic_learning_reminder']:
            png = (ASSETS / ('main/res/drawable-nodpi/' + name + '.png')).read_bytes()
            self.assertEqual(b'\x89PNG\r\n\x1a\n', png[:8])
            self.assertEqual(6, png[25])
        self.assertFalse((ASSETS / 'main/res/drawable/ic_learning_reminder.xml').exists())
        provenance = load(ASSETS / 'main/assets/brand-icons-provenance.json')
        self.assertTrue(provenance['ico_header']['source'].endswith('/ico_header.png'))
        self.assertTrue(provenance['ico_notif']['source'].endswith('/ico_notif.png'))

    def test_real_reminder_timing_and_search_dismissal(self):
        source = ASSETS / 'main/java/com/hydro/reedance'
        policy = (source / 'domain/ReminderPolicy.kt').read_text()
        worker = (source / 'notifications/LearningReminders.kt').read_text()
        self.assertIn('const val UNIT_MILLIS = 12 * 60 * 60 * 1000L', policy)
        for text in [policy, worker]:
            self.assertNotIn('testMode', text)
            self.assertNotIn('BuildConfig.DEBUG', text)
            self.assertNotIn('10_000L', text)
        search = (source / 'ui/SearchDismiss.kt').read_text()
        for token in ['BackHandler(session.focused)', 'PointerEventPass.Initial', '!session.bounds.contains', 'focus.clearFocus(force = true)', 'keyboard?.hide()']:
            self.assertIn(token, search)
        self.assertNotIn('.consume()', search)
        catalog = (source / 'ui/Catalog.kt').read_text()
        self.assertIn('shape = RoundedCornerShape(20.dp)', catalog)
        self.assertIn('onFocusChanged', catalog)
        self.assertIn('KeyboardActions(onSearch', catalog)

    def test_cover_clips_every_draw_and_resets_resource_state(self):
        source = (ASSETS / 'main/java/com/hydro/reedance/ui/Catalog.kt').read_text()
        self.assertIn('onDrawWithContent { clipPath(outline)', source)
        self.assertIn('addRoundRect(RoundRect(', source)
        self.assertIn('value = coverCache.get(resource) ?: withContext', source)
        self.assertNotIn('if(value == null)', source)

    def test_home_pager_and_real_persian_activity_ledger(self):
        source = ASSETS / 'main/java/com/hydro/reedance'
        pager = (source / 'ui/HomePager.kt').read_text()
        for token in ['HorizontalPager(', 'pageCount = { 2 }', 'fa_IR@calendar=persian', 'getActualMaximum(Calendar.DAY_OF_MONTH)', 'TimeZone.getTimeZone(zone.id)', 'day > month.today', 'ACTIVE_DAY_MILLIS']:
            self.assertIn(token, pager)
        activity = (source / 'data/ActivityRepository.kt').read_text()
        self.assertIn('ACTIVE_DAY_MILLIS = 120_000L', activity)
        self.assertIn('date.plusDays(1).atStartOfDay(zone)', activity)
        self.assertIn('preferencesDataStore("daily_activity")', activity)
        model = (source / 'data/AppModel.kt').read_text()
        self.assertIn('SystemClock.elapsedRealtime()', model)
        self.assertIn('if(completed) activity.complete()', model)
        self.assertIn('previous?.join()', model)
        main = (source / 'MainActivity.kt').read_text()
        self.assertIn('appModel.foregroundActivity(true)', main)
        self.assertIn('appModel.foregroundActivity(false)', main)

    def test_explore_magnifier_and_draw_only_banner_gradient(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        catalog = (ui / 'Catalog.kt').read_text()
        self.assertIn('leadingIcon = { AppIcon(UiIcons.Search, null, Modifier.size(20.dp)) }', catalog)
        provenance = load(ASSETS / 'main/assets/uicons-provenance.json')
        self.assertEqual('search', provenance['icons']['Search']['glyph'])
        self.assertEqual('Regular Rounded', provenance['icons']['Search']['style'])
        gradient = (ui / 'BannerGradient.kt').read_text()
        for token in ['Canvas(modifier)', 'withFrameNanos', 'repeatOnLifecycle(Lifecycle.State.STARTED)', 'animate && !reduced', 'RuntimeShader(FLUID_SHADER)', 'Brush.radialGradient']:
            self.assertIn(token, gradient)
        self.assertLess(gradient.index('Canvas(modifier)'), gradient.index('val t = seconds'))
        for token in ['fbm(p + warp * 2.6', 'float ribbon = exp(', 'float grain =', 'Build.VERSION.SDK_INT >= 33', 'else drawFluidFallback(t)', '32_000_000L']:
            self.assertIn(token, gradient)
        pager = (ui / 'HomePager.kt').read_text()
        self.assertIn('BannerGradient(animate, Modifier.matchParentSize())', pager)
        self.assertIn('animate = visible && pager.currentPage == 0', pager)
        self.assertIn('boundsInWindow()', pager)

    def test_banner_progress_and_vertical_message_cycle_stay_inside(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        pager = (ui / 'HomePager.kt').read_text()
        self.assertIn('BannerMessages(explore, animate, Modifier.fillMaxSize().padding(26.dp))', pager)
        messages = (ui / 'BannerMessages.kt').read_text()
        for token in ['BANNER_MESSAGE_MILLIS = 6_000', 'repeatOnLifecycle(Lifecycle.State.STARTED)', 'animate && !reduced', 'progress.animateTo(1f', 'index = (index + 1) % bannerCopies.size', 'progress.snapTo(0f)', 'slideInVertically(tween(450)) { it }', 'slideOutVertically(tween(450)) { -it }', 'LinearProgressIndicator(progress = { progress.value }', 'Modifier.weight(1f)']:
            self.assertIn(token, messages)
        self.assertLess(messages.index('PressButton(explore,'), messages.index('LinearProgressIndicator('))
        self.assertIn('YOUR RHYTHM. YOUR WORDS.', messages)

    def test_supplied_starter_artwork_in_home_and_course(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        self.assertIn('StarterArtwork(Modifier.size(104.dp))', (ui / 'Catalog.kt').read_text())
        self.assertIn('StarterArtwork(Modifier.size(96.dp))', (ui / 'Starter.kt').read_text())
        artwork = (ui / 'StarterArtwork.kt').read_text()
        self.assertIn('R.drawable.reedance_starter', artwork)
        self.assertIn('ContentScale.Fit', artwork)
        self.assertIn('val fa = LocalFa.current', artwork)
        self.assertIn('scaleX = if(fa) -1f else 1f', artwork)
        self.assertNotIn('ContentScale.Crop', artwork)
        png = (ASSETS / 'main/res/drawable-nodpi/reedance_starter.png').read_bytes()
        self.assertEqual((384,384), struct.unpack('>II', png[16:24]))
        self.assertEqual(6, png[25])
        provenance = load(ASSETS / 'main/assets/brand-icons-provenance.json')
        self.assertTrue(provenance['ico_starter']['source'].endswith('/ico_starter.png'))

    def test_starter_float_and_banner_explore_action(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        artwork = (ui / 'StarterArtwork.kt').read_text()
        for token in ['visible && !reduced', 'repeatOnLifecycle(Lifecycle.State.STARTED)', 'shift.animateTo(-4f', 'shift.animateTo(4f', 'translationY = shift.value.dp.toPx()', 'boundsInWindow()']:
            self.assertIn(token, artwork)
        messages = (ui / 'BannerMessages.kt').read_text()
        self.assertIn('PressButton(explore,', messages)
        self.assertNotIn('UiIcons.Play', messages)
        self.assertNotIn('midnight-avenue', messages)
        app = (ui / 'App.kt').read_text()
        self.assertIn('com.hydro.reedance.StarterActivity::class.java', app)
        self.assertIn('{ navigate("explore") })', app)
        catalog = (ui / 'Catalog.kt').read_text()
        self.assertIn('PressButton(starter, Modifier.fillMaxWidth().heightIn(min = 52.dp)', catalog)
        self.assertNotIn('PressTextButton(starter)', catalog)

    def test_starter_is_separate_activity_with_own_header_and_language_picker(self):
        source = ASSETS / 'main/java/com/hydro/reedance'
        manifest = (ASSETS / 'main/AndroidManifest.xml').read_text()
        self.assertIn('android:name=".StarterActivity" android:exported="false" android:theme="@style/Theme.Reedance"', manifest)
        activity = (source / 'StarterActivity.kt').read_text()
        self.assertIn('class StarterActivity: ComponentActivity()', activity)
        self.assertIn('StarterDestination(model, ::finish)', activity)
        self.assertNotIn('installSplashScreen()', activity)
        self.assertIn('model.foregroundActivity(true)', activity)
        self.assertIn('model.foregroundActivity(false)', activity)
        app = (source / 'ui/App.kt').read_text()
        self.assertNotIn('"starter" -> StarterScreen', app)
        self.assertIn('context.startActivity(android.content.Intent(context, com.hydro.reedance.StarterActivity::class.java))', app)
        destination = (source / 'ui/StarterDestination.kt').read_text()
        for token in ['PressIconButton(back', 'tr("استارتر", "Starter")', 'ReedanceBottomSheet', 'Modifier.size(32.dp).clip(CircleShape)', 'LocalOverlayBottomInset provides padding.calculateBottomPadding()', 'StarterScreen(model, selected.first)']:
            self.assertIn(token, destination)
        self.assertNotIn('FloatingNavigation', destination)
        self.assertNotIn('MainHeader(', destination)
        starter = (source / 'ui/Starter.kt').read_text()
        self.assertIn('rememberSaveable(code)', starter)
        self.assertNotIn('ChoiceRow(listOf("English"', starter)

    def test_starter_preload_and_transparent_system_navigation(self):
        source = ASSETS / 'main/java/com/hydro/reedance'
        activity = (source / 'StarterActivity.kt').read_text()
        self.assertIn('window.isNavigationBarContrastEnforced = false', activity)
        self.assertIn('starterOnly = true', activity)
        model = (source / 'data/AppModel.kt').read_text()
        for token in ['@JvmOverloads constructor', 'StarterPreload.snapshot()', 'MutableStateFlow(preload != null)', 'preload?.courses ?: JsonObject()', 'preload?.settings ?: Settings()', 'preload?.steps ?: emptyList()', 'if(starterOnly)']:
            self.assertIn(token, model)
        repo = (source / 'data/LocalContentRepository.kt').read_text()
        self.assertIn('StarterPreload.loadMutex.withLock', repo)
        self.assertIn('StarterPreload.courses = it', repo)
        cache = (source / 'data/StarterPreload.kt').read_text()
        self.assertNotIn('Activity:', cache)
        self.assertIn('@Volatile var courses', cache)
        destination = (source / 'ui/StarterDestination.kt').read_text()
        self.assertNotIn('.padding(padding)', destination)
        self.assertIn('LocalOverlayBottomInset provides padding.calculateBottomPadding()', destination)

    def test_all_sheets_use_blue_white_shared_palette(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        sheet = (ui / 'ReedanceBottomSheet.kt').read_text()
        for token in ['Color(0xFF062FFD)', 'containerColor = SheetBlue', 'contentColor = Color.White', 'primary = Color.White', 'onSurface = Color.White', 'tonalElevation = 0.dp']:
            self.assertIn(token, sheet)
        for name in ['MainHeader.kt', 'StarterDestination.kt', 'Song.kt']:
            source = (ui / name).read_text()
            self.assertIn('ReedanceBottomSheet(', source)
            self.assertNotIn('ModalBottomSheet(', source)

    def test_sheets_animate_before_removal_and_pause_background_motion(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        sheet = (ui / 'ReedanceBottomSheet.kt').read_text()
        for token in ['skipPartiallyExpanded = true', 'sheetState.hide()', 'if(!sheetState.isVisible)', 'afterHidden()', 'motion.overlays++', 'motion.overlays--', 'if(!closing)']:
            self.assertIn(token, sheet)
        self.assertLess(sheet.index('sheetState.hide()'), sheet.index('afterHidden()'))
        self.assertIn('dismissSheet { onLanguage(language.code) }', (ui / 'MainHeader.kt').read_text())
        self.assertIn('dismissSheet { language = code }', (ui / 'StarterDestination.kt').read_text())
        for name in ['BannerGradient.kt', 'BannerMessages.kt', 'StarterArtwork.kt', 'MainHeader.kt']:
            source = (ui / name).read_text()
            self.assertIn('LocalBackgroundMotion.current.paused', source)
            self.assertIn('!paused', source)

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
