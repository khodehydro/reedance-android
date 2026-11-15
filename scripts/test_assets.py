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
    def test_directory_sync_uses_public_android_sdk_and_checks_descriptor(self):
        # Source regression guard only; native compilation/device fsync need Android CI.
        source = (ASSETS / 'main/java/com/hydro/reedance/install/PackInstaller.kt').read_text()
        self.assertNotIn('OsConstants.O_DIRECTORY', source)
        self.assertIn('Os.open(directory.path, android.system.OsConstants.O_RDONLY, 0)', source)
        self.assertIn('OsConstants.S_ISDIR(android.system.Os.fstat(fd).st_mode)', source)
        self.assertIn('android.system.Os.fsync(fd)', source)
        self.assertIn('finally { android.system.Os.close(fd) }', source)

    def test_desktop_password_vectors_and_authenticated_container(self):
        import unicodedata
        data = load(ASSETS / 'test/resources/password-v1.json')
        whitespace = ''.join(chr(c) for c in [9,10,11,12,13,32,160,5760,*range(8192,8203),8232,8233,8239,8287,12288,65279])
        keys = []
        for vector in data['vectors']:
            normalized = unicodedata.normalize('NFKC', vector['password']).strip(whitespace)
            key = hashlib.pbkdf2_hmac('sha256', normalized.encode(), b'reedance-pack-v1', 200000, 32)
            self.assertEqual(key.hex(), vector['key'])
            keys.append(key)
        pack = bytes.fromhex(data['packHex'])
        self.assertEqual(hashlib.sha256(pack).hexdigest(), data['packSha256'])
        length = pack[8]; header = pack[:29 + length]
        self.assertEqual(pack[9:9+length], b'enc-main')
        self.assertEqual(AESGCM(keys[0]).decrypt(header[9+length:21+length], pack[len(header):], header).hex(), data['plaintextHex'])
        from cryptography.exceptions import InvalidTag
        with self.assertRaises(InvalidTag):
            AESGCM(keys[1]).decrypt(header[9+length:21+length], pack[len(header):], header)

    def test_password_storage_and_local_import_remain_distinct_from_signed_downloads(self):
        install = (ASSETS / 'main/java/com/hydro/reedance/install/PackInstaller.kt').read_text()
        self.assertNotIn('config.obj("aesKeys")', install)
        self.assertIn('PackVerifier.manifest(', install)
        self.assertIn('if(!local) dao.saveSeen(', install)
        self.assertIn('Local imports cannot replace a signed publisher installation', install)
        self.assertIn('validateAndActivate(stage, zip, payload, expectedSong, expectedLanguage, null, progress)', install)
        storage = (ASSETS / 'main/java/com/hydro/reedance/install/PackKeyStore.kt').read_text()
        self.assertIn('AndroidKeyStore', storage)
        self.assertIn('cipher.doFinal(key)', storage)
        self.assertNotIn('putString("password"', storage)
        ui = (ASSETS / 'main/java/com/hydro/reedance/ui/PackControls.kt').read_text()
        self.assertNotIn('rememberSaveable', ui.split('@Composable fun PackControls')[1])
        self.assertIn('PasswordVisualTransformation()', ui)

    def test_bundled_gulum_export_matches_pinned_index_and_manifest(self):
        index = load(ASSETS / 'main/assets/bundled_packs.json')['gulum-2']
        root = ASSETS / 'main/assets/packs/gulum-2'
        envelope = load(root / 'latest.json')
        payload = json.loads(b64(envelope['payload']))
        spec = payload['package']
        self.assertEqual((payload['songId'], payload['targetLanguage'], payload['contentVersionCode']), ('gulum-2', 'tr', 2))
        self.assertEqual(spec['encryptionKeyId'], 'enc-main')
        self.assertEqual(index['fileName'], spec['fileName'])
        pack = (root / spec['fileName']).read_bytes()
        self.assertEqual(len(pack), spec['sizeBytes'])
        self.assertEqual(len(pack), index['sizeBytes'])
        self.assertEqual(hashlib.sha256(pack).hexdigest(), spec['sha256'])
        self.assertEqual(index['sha256'], spec['sha256'])
        self.assertEqual(pack[:8], b'RDPACK1\0')
        key_len = pack[8]
        self.assertEqual(pack[9:9+key_len], b'enc-main')
        plain_len = struct.unpack('>Q', pack[21+key_len:29+key_len])[0]
        self.assertEqual(len(pack), 29 + key_len + plain_len + 16)
        self.assertEqual((root / 'latest.json').read_bytes(), (root / 'manifests' / spec['fileName'].replace('.rdpack', '.json')).read_bytes())
        self.assertEqual(len(b64(envelope['signature'])), 64)  # Shape only, not signature verification.
        song = next(s for s in load(ASSETS / 'main/assets/catalog_preview.json') if s['id'] == 'gulum-2')
        self.assertTrue(song['bundledPack'])
        self.assertEqual(song['language'], 'tr')
        installer = (ASSETS / 'main/java/com/hydro/reedance/install/PackInstaller.kt').read_text()
        self.assertIn('context.assets.open("packs/$song/$name")', installer)
        self.assertIn('song, language, progress)', installer)

    def test_mini_player_tracks_active_media_and_uses_solid_white_controls(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        shell = (ui / 'App.kt').read_text()
        mini = (ui / 'MiniPlayer.kt').read_text()
        model = (ASSETS / 'main/java/com/hydro/reedance/data/AppModel.kt').read_text()
        self.assertIn('model.activePlayback.collectAsStateWithLifecycle()', shell)
        self.assertIn('songId = active.songId; navigate("song")', shell)
        self.assertIn('model::toggleActivePlayback', shell)
        mini_toggle = model.split('fun toggleActivePlayback()')[1].split('fun preparePlayback()')[0]
        self.assertNotIn('prepareMedia()', mini_toggle)
        self.assertNotIn('lesson.value', mini_toggle)
        self.assertIn('color = MaterialTheme.colorScheme.surfaceContainerLowest', mini)
        self.assertNotIn('BannerGradient(', mini)
        gradient = (ui / 'BannerGradient.kt').read_text()
        self.assertIn('MIDNIGHT_FLUID_SHADER', gradient)
        self.assertNotIn('seconds * if(palette == FluidPalette.Midnight) .35f', gradient)
        self.assertIn('position.x / resolution.x - 0.5', gradient)
        self.assertIn('float t = time * 0.30', gradient)
        self.assertIn('val drift = if(midnight) .55f else .14f', gradient)
        self.assertIn('if(palette == FluidPalette.Midnight) 66_000_000L', gradient)
        self.assertIn('contentColor = MaterialTheme.colorScheme.onSurface', mini)
        self.assertIn('UiIcons.PauseFilled else UiIcons.PlayFilled', mini)
        self.assertIn('Modifier.size(18.dp)', mini)
        self.assertIn('Modifier.size(32.dp).clip(CircleShape)', mini)
        self.assertIn('song.title', mini)
        self.assertIn('UiIcons.CoffeeFilled', mini)
        self.assertIn('if(shareDialog) ShareAppDialog', mini)
        self.assertIn('Intent.ACTION_SEND', mini)
        self.assertIn('withContext(Dispatchers.IO)', mini)
        icons = load(ASSETS / 'main/assets/uicons-provenance.json')['icons']
        for name in ['PlayFilled', 'PauseFilled', 'CoffeeFilled']:
            self.assertEqual(icons[name]['style'], 'Solid Rounded')
            self.assertTrue((ASSETS / ('main/res/drawable/' + icons[name]['drawable'] + '.xml')).exists())

    def test_player_metadata_navigation_palette_and_square_share_card(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        song = (ui / 'Song.kt').read_text()
        player = song.split('@Composable private fun PlayerPanel')[1]
        self.assertNotIn('Midnight Avenue', player)
        self.assertNotIn('Reedance Originals', player)
        self.assertIn('Foreign(data.s("title"), large = true)', player)
        self.assertIn('Text(data.s("artist")', player)
        self.assertIn('PlayerPanel(model, data)', song)
        nav = (ui / 'FloatingNavigation.kt').read_text()
        self.assertIn('color = Color(0xFF0A2EFE), contentColor = Color.White', nav)
        self.assertIn('val tint = Color.White', nav)
        dialog = (ui / 'ShareAppDialog.kt').read_text()
        self.assertIn('Dialog(onDismissRequest = onDismiss)', dialog)
        self.assertIn('.aspectRatio(1f)', dialog)
        self.assertIn('FluidPalette.Aurora', dialog)
        self.assertIn('UiIcons.ShareFilled', dialog)
        self.assertIn('verticalScroll(rememberScrollState())', dialog)
        self.assertIn('backgroundMotion.overlays++', dialog)
        self.assertIn('backgroundMotion.overlays--', dialog)
        icons = load(ASSETS / 'main/assets/uicons-provenance.json')['icons']
        self.assertEqual(icons['ShareFilled']['style'], 'Solid Rounded')

    def test_installed_song_entry_and_wave_controller(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        song = (ui / 'Song.kt').read_text()
        self.assertIn('model.cachedLesson(songId)', song)
        self.assertIn('var loading by remember(songId) { mutableStateOf(true) }', song)
        self.assertIn('SongEntryState.NOT_INSTALLED', song)
        self.assertIn('SongEntryState.LOADING', song)
        self.assertIn('try { model.openInstalled(songId) }', song)
        self.assertIn('SongArtwork(songId', song)
        self.assertIn('Modifier.size(40.dp).clip(CircleShape)', song)
        controller = song.split('@Composable private fun PlayerPanel')[1]
        self.assertIn('BannerGradient(animate = playing, modifier = Modifier.matchParentSize())', controller)
        self.assertIn('contentColor = Color.White', controller)
        self.assertIn('fontSize = 11.sp', controller)
        self.assertNotIn('Modifier.size(64.dp)', controller)
        self.assertIn('WaveSeekBar(', controller)
        wave = (ui / 'WaveSeekBar.kt').read_text()
        self.assertIn('Slider(value = value.coerceIn', wave)
        self.assertIn('Offset(end, center), Offset(size.width, center)', wave)
        self.assertIn('drawPath(path, Color.White', wave)
        self.assertIn('if(playing && !reduced && !paused)', wave)
        self.assertIn('contentDescription = description', wave)
        self.assertIn('repeatOnLifecycle(Lifecycle.State.STARTED)', wave)

    def test_thumb_free_seek_compact_controller_and_entry_motion(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        wave = (ui / 'WaveSeekBar.kt').read_text()
        self.assertIn('thumb = { Spacer(Modifier.size(0.dp)) }', wave)
        self.assertNotIn('background(Color.White, CircleShape)', wave)
        self.assertIn('Slider(value = value.coerceIn', wave)
        song = (ui / 'Song.kt').read_text()
        self.assertIn('scroll.firstVisibleItemIndex > 0', song)
        self.assertIn('AnimatedVisibility(available && compactController', song)
        self.assertIn('CompactSongController(model, data', song)
        self.assertIn('scroll.animateScrollToItem(0)', song)
        self.assertIn('BookmarkButton(', song)
        self.assertIn('model.toggleBookmark("song", songId, songId)', song)
        bookmark = (ui / 'BookmarkMotion.kt').read_text()
        self.assertIn('UiIcons.BookmarkFilled else UiIcons.Bookmark', bookmark)
        self.assertIn('AnimatedContent(saved', bookmark)
        self.assertIn('PressIconButton(onClick', bookmark)
        self.assertIn('selected = saved; stateDescription = state', bookmark)
        entry = (ui / 'EntryMotion.kt').read_text()
        self.assertIn('remember(destination)', entry)
        self.assertIn('graphicsLayer', entry)
        self.assertIn('rememberSaveable', entry)
        self.assertIn('if(reduced) progress.snapTo(1f)', entry)
        self.assertNotIn('AnimatedContent', entry)
        self.assertIn('PageEntrance(if(page == "song")', (ui / 'App.kt').read_text())
        self.assertIn('.itemEntrance()', (ui / 'Components.kt').read_text())
        self.assertIn('modifier.itemEntrance()', (ui / 'Catalog.kt').read_text())
        self.assertIn('PageEntrance("starter:', (ui / 'StarterDestination.kt').read_text())
        icons = load(ASSETS / 'main/assets/uicons-provenance.json')['icons']
        self.assertEqual('Solid Rounded', icons['BookmarkFilled']['style'])

    def test_dark_theme_uses_neutral_charcoal_surfaces_and_readable_text(self):
        import re
        source = (ASSETS / 'main/java/com/hydro/reedance/ui/Theme.kt').read_text()
        palette = source.split('internal val CharcoalDarkColors = darkColorScheme(')[1].split('@Composable fun tr')[0]
        colors = {key: tuple(int(value[i:i+2], 16) / 255 for i in (2,4,6))
                  for key, value in re.findall(r'(\w+) = Color\(0x([A-F0-9]{8})\)', palette)}
        for role in ['background', 'surface', 'surfaceVariant', 'surfaceDim', 'surfaceBright',
                     'surfaceContainerLowest', 'surfaceContainerLow', 'surfaceContainer',
                     'surfaceContainerHigh', 'surfaceContainerHighest', 'primaryContainer',
                     'secondaryContainer', 'tertiaryContainer', 'outline', 'outlineVariant']:
            r, g, b = colors[role]
            self.assertEqual(r, g, role)
            self.assertEqual(g, b, role)
        def luminance(rgb):
            linear = [x / 12.92 if x <= .04045 else ((x + .055) / 1.055) ** 2.4 for x in rgb]
            return sum(x * w for x, w in zip(linear, [.2126, .7152, .0722]))
        for text, background in [('onBackground', 'background'), ('onSurface', 'surface'),
                                 ('onSurfaceVariant', 'surfaceContainerHighest'),
                                 ('onPrimaryContainer', 'primaryContainer'),
                                 ('onSecondaryContainer', 'secondaryContainer'), ('onPrimary', 'primary')]:
            a, b = sorted([luminance(colors[text]), luminance(colors[background])])
            self.assertGreaterEqual((b + .05) / (a + .05), 4.5, text)
        self.assertIn('surfaceTint = Color.Transparent', palette)
        self.assertIn('if(dark) CharcoalDarkColors', source)
        self.assertIn('else lightColorScheme(primary = Blue, background = Paper, surface = Color.White', source)

    def test_header_wordmark_uses_real_vazirmatn_black_and_navigation_avoids_fullscreen_alpha(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        theme = (ui / 'Theme.kt').read_text()
        header = (ui / 'MainHeader.kt').read_text()
        self.assertIn('BrandFont = FontFamily(Font(R.font.vazirmatn_black, FontWeight.Black))', theme)
        self.assertIn('fontFamily = BrandFont, fontWeight = FontWeight.Black', header)
        font = (ASSETS / 'main/res/font/vazirmatn_black.ttf').read_bytes()
        tables = struct.unpack_from('>H', font, 4)[0]
        weights = []
        for i in range(tables):
            tag, _, offset, _ = struct.unpack_from('>4sIII', font, 12 + 16 * i)
            if tag == b'OS/2': weights.append(struct.unpack_from('>H', font, offset + 4)[0])
        self.assertEqual([900], weights)
        provenance = load(ASSETS / 'main/assets/fonts-provenance.json')['headerWordmark']
        self.assertEqual(hashlib.sha256(font).hexdigest(), provenance['sha256'])
        entry = (ui / 'EntryMotion.kt').read_text()
        page = entry.split('@Composable internal fun Modifier.itemEntrance')[0]
        self.assertIn('.offset {', page)
        self.assertNotIn('.graphicsLayer {', page)
        self.assertNotIn('alpha =', page)
        self.assertIn('compositingStrategy = CompositingStrategy.ModulateAlpha', entry)
        app = (ui / 'App.kt').read_text()
        self.assertIn('key(page) {', app)
        self.assertIn('pageStates.SaveableStateProvider(page)', app)

    def test_solid_minimized_players_icon_tabs_and_line_play_action(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        song = (ui / 'Song.kt').read_text()
        compact = song.split('@Composable private fun CompactSongController')[1]
        self.assertIn('color = MaterialTheme.colorScheme.surfaceContainerLowest', compact)
        self.assertNotIn('BannerGradient(', compact)
        full = song.split('@Composable private fun PlayerPanel')[1].split('@Composable private fun CompactSongController')[0]
        self.assertIn('BannerGradient(', full)
        self.assertIn('model.playFromLine(songId, line.n("startMs"))', song)
        self.assertNotIn('Text(clock(line.n("startMs")))', song)
        self.assertNotIn('Tap a timestamp', song)
        lyric = (ui / 'LyricLine.kt').read_text()
        self.assertIn('PressIconButton(onPlay', lyric)
        self.assertIn('UiIcons.PauseFilled else UiIcons.PlayFilled', lyric)
        self.assertIn('if(mode == 1)', lyric)
        self.assertIn('interfaceDirection', lyric)
        self.assertIn('if(active) option.filledIcon ?: option.icon else option.icon', (ui / 'Components.kt').read_text())
        icons = load(ASSETS / 'main/assets/uicons-provenance.json')['icons']
        for name in ['Lyrics', 'Notes', 'Words', 'Phrases', 'Original', 'Lines', 'WordMode']:
            self.assertEqual('Regular Rounded', icons[name]['style'])
            self.assertEqual('Solid Rounded', icons[name + 'Filled']['style'])
            self.assertIn('filledIcon = UiIcons.' + name + 'Filled', (ui / 'LessonTabs.kt').read_text())
        model = (ASSETS / 'main/java/com/hydro/reedance/data/AppModel.kt').read_text()
        command = model.split('fun playFromLine(')[1].split('fun pause()')[0]
        self.assertLess(command.index('player.seekTo('), command.index('player.play()'))
        self.assertIn('player.currentMediaItem?.mediaId == songId', command)

    def test_no_demo_or_mock_catalog_ships_in_any_app_variant(self):
        catalog = load(ASSETS / 'main/assets/catalog_preview.json')
        self.assertEqual(['gulum-2'], [song['id'] for song in catalog])
        self.assertTrue(all(song.get('bundledPack') for song in catalog))
        for variant in ['main', 'debug', 'release']:
            assets = ASSETS / variant / 'assets'
            self.assertFalse((assets / 'demo_lesson.json').exists())
            self.assertFalse((assets / 'midnight-avenue-demo.wav').exists())
        for file in (ASSETS / 'main/java').rglob('*.kt'):
            text = file.read_text()
            self.assertNotIn('installDemo', text)
            self.assertNotIn('restoreDemo', text)
            self.assertNotIn('midnight-avenue', text)

    def test_item_bookmarks_are_icon_only_at_trailing_edge_and_lyric_pause_is_scoped(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        song = (ui / 'Song.kt').read_text()
        action = song.split('@Composable fun SaveAction')[1].split('@Composable private fun PlayerPanel')[0]
        self.assertIn('horizontalArrangement = Arrangement.End', action)
        self.assertIn('ItemBookmarkButton(saved', action)
        self.assertNotIn('Text(', action)
        self.assertIn('playing && active && activePlayback?.songId == songId', song)
        self.assertIn(') model.pause()', song)
        self.assertIn('else model.playFromLine(', song)
        self.assertIn('if(playing) UiIcons.PauseFilled else UiIcons.PlayFilled', (ui / 'LyricLine.kt').read_text())

    def test_single_lyric_view_tracks_media_clock_and_preserves_list_mode(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        song = (ui / 'Song.kt').read_text()
        self.assertIn('singleLyric by rememberSaveable(songId)', song)
        self.assertIn('LessonTabs(tab, setTab, mode', song)
        self.assertIn('val visibleLines = lines.take(access.limit("lyrics"))', song)
        self.assertIn('item(key = "single-lyric"', song)
        self.assertIn('TimedLyricBox(model, songId, visibleLines, renderLine)', song)
        helper = (ui / 'LyricLayout.kt').read_text()
        self.assertIn('lines.lastOrNull { it.n("startMs") <= positionMs } ?: lines.firstOrNull()', helper)
        self.assertNotIn('delay(', helper)

    def test_single_lyrics_centered_without_play_and_song_tabs_fill_width(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        lyric = (ui / 'LyricLine.kt').read_text()
        self.assertIn('if(!single) PressIconButton', lyric)
        self.assertIn('if(single) TextAlign.Center', lyric)
        self.assertIn('Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)', lyric)
        song = (ui / 'Song.kt').read_text()
        self.assertIn('single = singleLyric', song)
        self.assertIn('LessonTabs(tab, setTab, mode', song)
        tabs = (ui / 'Components.kt').read_text()
        self.assertIn('if(expanded) Modifier.weight(1f)', tabs)
        self.assertIn('if(expanded) Modifier else Modifier.horizontalScroll', tabs)

    def test_connected_lesson_tabs_include_quiz_without_separate_button(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        song = (ui / 'Song.kt').read_text()
        self.assertNotIn('item(key = "quiz")', song)
        self.assertNotIn('item(key = "lyric-mode")', song)
        tabs = (ui / 'LessonTabs.kt').read_text()
        self.assertIn('if(index == 4) onQuiz() else onTab(index)', tabs)
        self.assertIn('if(tab == 0) Canvas', tabs)
        self.assertIn('cubicTo(', tabs)
        self.assertIn('if(rtl) size.width - x else x', tabs)
        self.assertIn('if(LocalReducedMotion.current) 0 else 260', tabs)

    def test_review_migration_preserves_progress_and_separates_duplicate_lexemes(self):
        import sqlite3, re
        source = (ASSETS / 'main/java/com/hydro/reedance/data/Database.kt').read_text()
        db = sqlite3.connect(':memory:')
        db.executescript("""
            CREATE TABLE ReviewCard(lexemeId TEXT PRIMARY KEY, stage INTEGER, dueAt INTEGER, savedAt INTEGER);
            CREATE TABLE Bookmark(songId TEXT, itemType TEXT, itemId TEXT);
            INSERT INTO ReviewCard VALUES ('same', 3, 1234, 56), ('orphan', 2, 444, 55);
            INSERT INTO Bookmark VALUES ('turkish', 'vocabulary', 'same'), ('english', 'vocabulary', 'same');
        """)
        migration = source.split('override fun migrate(')[1].split('@Volatile')[0]
        for sql in re.findall(r'db.execSQL\("([^"\n]+)"\)', migration): db.execute(sql)
        self.assertEqual([('english', 'same', 3), ('turkish', 'same', 3), ('', 'orphan', 2)],
            db.execute('SELECT songId, lexemeId, stage FROM ReviewCard ORDER BY lexemeId DESC, songId').fetchall())
        db.execute("UPDATE ReviewCard SET stage=5 WHERE songId='turkish'")
        self.assertEqual(3, db.execute("SELECT stage FROM ReviewCard WHERE songId='english'").fetchone()[0])
        self.assertIn('addMigrations(MIGRATION_1_2)', source)

    def test_language_scoped_learning_and_written_pronunciations(self):
        model = (ASSETS / 'main/java/com/hydro/reedance/data/AppModel.kt').read_text()
        self.assertIn('it.s("language") == prefs.targetLanguage', model)
        for token in ['combine(dao.bookmarks(), languageSongIds)', 'combine(dao.reviews(), languageSongIds)', 'combine(dao.results(), languageSongIds)', 'dao.review(card.lexemeId, card.songId)']:
            self.assertIn(token, model)
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        library = (ui / 'Library.kt').read_text()
        self.assertIn('model.lessonForReview(it.songId)', library)
        self.assertIn('savedLessons[mark.songId]', library)
        self.assertIn('model.toggleBookmark(mark.itemType, mark.itemId, mark.songId)', library)
        for name in ['Song.kt', 'Starter.kt', 'Quiz.kt', 'Components.kt']:
            self.assertNotIn('model.speak', (ui / name).read_text())
        extra = (ui / 'SongLearningExtras.kt').read_text()
        self.assertIn('.filter { it.isNotBlank() }.distinct()', extra)
        self.assertIn('SongQuizInvitation(openQuiz)', (ui / 'Song.kt').read_text())

    def test_practice_choices_submit_on_tap_and_only_errors_wait_for_continue(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        quiz = (ui / 'Quiz.kt').read_text()
        self.assertIn('PracticeChoices(q, answer.singleOrNull(), checked, busy) { submit(listOf(it)) }', quiz)
        self.assertIn('if(!busy && checked == null && complete(q, submitted))', quiz)
        self.assertIn('if(q.s("type") != "choice") Action', quiz)
        self.assertIn('if(!checked!!.correct) Action', quiz)
        effect = quiz.split('LaunchedEffect(index, checked, data, review)')[1].split('    Column')[0]
        self.assertIn('if(result.correct && index < questions.size)', effect)
        self.assertIn('delay(850)', effect)
        self.assertIn('index++', effect)
        choices = (ui / 'PracticeChoices.kt').read_text()
        for token in ['id == question.s("correctId")', 'id == selected && !correct', 'Color(0xFF176B3A)', 'Color(0xFFA82232)', 'disabledContainerColor = background', 'disabledContentColor = foreground', 'enabled = result == null && !busy', 'stateDescription = state']:
            self.assertIn(token, choices)

    def test_match_practice_uses_two_columns_measured_connections_and_solution(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        quiz = (ui / 'Quiz.kt').read_text()
        self.assertIn('"match" -> MatchBoard(', quiz)
        self.assertIn('if(q.s("type") == "match" && !checked!!.correct)', quiz)
        self.assertNotIn('if(!checked!!.correct ||', quiz)
        self.assertIn('MatchBoard(q, correctAnswer(q), true, false, onAnswer = {}, solution = true)', quiz)
        board = (ui / 'MatchBoard.kt').read_text()
        for token in ['Column(Modifier.weight(1f)', 'boundsInParent()', 'cubicTo(mid, y1, mid, y2, x2, y2)', 'if(pending != null) commit(', 'enabled && !checked && !solution', 'MatchGreen', 'MatchRed', 'stateDescription = state', 'answer.getOrNull(index) == meaning -> ""']:
            self.assertIn(token, board)
        self.assertNotIn('PressFilterChip', board)

    def test_match_grading_and_feedback_share_visible_meaning_equivalence(self):
        source = (ASSETS / 'main/java/com/hydro/reedance/domain/Learning.kt').read_text()
        self.assertIn('fun matchCorrectness(', source)
        self.assertIn('expected.s(meaningLanguage)', source)
        self.assertIn('chosen.s(meaningLanguage)', source)
        self.assertIn('expectedText.isNotBlank()', source)
        self.assertIn('counts[chosenId] == 1', source)
        self.assertIn('complete(q, answer) && matches.all { it }', source)
        self.assertIn('!matches[i]', source)
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        self.assertIn('matchCorrectness(question, values, if(fa) "fa" else "en")', (ui / 'MatchBoard.kt').read_text())
        self.assertIn('grade(q, submitted, data.s("locale"), if(fa) "fa" else "en")', (ui / 'Quiz.kt').read_text())

    def test_sentence_parts_return_individually_and_typing_has_translated_context(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        quiz = (ui / 'Quiz.kt').read_text()
        self.assertIn('"order" -> SentenceOrder(q, answer, checked, busy)', quiz)
        self.assertNotIn('Reset order', quiz)
        self.assertIn('typingSentenceMeaning(q, data, fa)', quiz)
        self.assertIn('RoundedCornerShape(18.dp), singleLine = true', (ui / 'LearningText.kt').read_text())
        self.assertIn('q.s("type") in listOf("order", "typing")', quiz)
        sentence = (ui / 'SentencePractice.kt').read_text()
        self.assertIn('answer.filterIndexed { i, _ -> i != index }', sentence)
        self.assertIn('order.filter { it !in answer }', sentence)
        self.assertIn('Color(0xFF176B3A)', sentence)
        self.assertIn('Color(0xFFA82232)', sentence)
        self.assertIn('disabledContentColor = foreground', sentence)
        self.assertIn('val editable = result == null && !busy', sentence)
        self.assertIn('question.tr("translation", fa)', sentence)
        self.assertNotIn('tr("explanation"', sentence)
        schema = load(ROOT / 'contracts/v1/lesson.schema.json')
        # These are optional so older signed/exported packages remain compatible.
        question = schema['$defs']['questionBase']
        self.assertIn('translationFa', question['properties'])
        self.assertNotIn('translationFa', question['required'])

    def test_teaching_language_is_persian_independent_of_ui_language(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        policy = (ASSETS / 'main/java/com/hydro/reedance/data/LearningLanguage.kt').read_text()
        self.assertIn('const val persian = true', policy)
        self.assertIn('const val meaningKey = "fa"', policy)
        for name in ['Song', 'Quiz', 'Starter', 'Components', 'LyricLine', 'Library', 'MatchBoard', 'PracticeChoices', 'SongLearningExtras']:
            source = (ui / (name + '.kt')).read_text()
            self.assertIn('val fa = LearningLanguage.persian', source)
            self.assertNotIn('LocalFa.current', source)
        self.assertIn('if(LocalFa.current) fa else en', (ui / 'Theme.kt').read_text())
        learning = (ASSETS / 'main/java/com/hydro/reedance/domain/Learning.kt').read_text()
        self.assertIn('meaningLanguage: String = LearningLanguage.meaningKey', learning)
        self.assertIn('val interfaceDirection = LayoutDirection.Rtl', (ui / 'LyricLine.kt').read_text())

    def test_learning_paragraph_direction_does_not_follow_interface_locale(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        helper = (ui / 'LearningText.kt').read_text()
        for token in ['fun PersianText(', 'modifier.fillMaxWidth()', 'textDirection = TextDirection.Rtl, textAlign = TextAlign.Right', '"rtl" -> true', '"ltr" -> false', 'textDirection = TextDirection.Ltr, textAlign = TextAlign.Left']:
            self.assertIn(token, helper)
        components = (ui / 'Components.kt').read_text()
        self.assertIn('PersianText(note.tr("body", fa)', components)
        self.assertIn('LearningCell(cell.cell(fa)', components)
        self.assertIn('textDirection = TextDirection.Ltr, textAlign =', components)
        quiz = (ui / 'Quiz.kt').read_text()
        self.assertIn('PersianText(q.tr("prompt", fa)', quiz)
        self.assertIn('PersianText(q.tr("explanation", fa)', quiz)
        self.assertIn('"typing" -> PracticeTypingField(', quiz)
        self.assertIn('PersianText((if(correct)', (ui / 'PracticeChoices.kt').read_text())

    def test_lesson_matches_unchanged_v1_schema(self):
        Draft202012Validator(load(ROOT / 'contracts/v1/lesson.schema.json')).validate(load(ROOT / 'tests/fixtures/legacy-demo/demo_lesson.json'))

    def test_bootstrap_has_no_invented_urls(self):
        catalog = load(ASSETS / 'main/assets/bootstrap_catalog.json')
        Draft202012Validator(load(ROOT / 'contracts/v1/bootstrap-catalog.schema.json')).validate(catalog)
        self.assertEqual([], catalog['songs'])

    def test_audio_matches_lesson_and_markers(self):
        lesson = load(ROOT / 'tests/fixtures/legacy-demo/demo_lesson.json')
        audio = ROOT / 'tests/fixtures/legacy-demo/midnight-avenue-demo.wav'
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
        lesson = load(ROOT / 'tests/fixtures/legacy-demo/demo_lesson.json')
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
        self.assertIn('SongArtwork(songId', song)
        self.assertIn('Modifier.size(40.dp).clip(CircleShape)', song)
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
        self.assertIn('!settings.permissionsSetup -> PageEntrance("permissions") { PermissionSetup(model) }', app)
        manifest = (ASSETS / 'main/AndroidManifest.xml').read_text()
        self.assertIn('android.permission.POST_NOTIFICATIONS', manifest)
        for permission in ['WRITE_EXTERNAL_STORAGE', 'READ_EXTERNAL_STORAGE', 'MANAGE_EXTERNAL_STORAGE', 'SCHEDULE_EXACT_ALARM']:
            self.assertNotIn(permission, manifest)
        worker = (ASSETS / 'main/java/com/hydro/reedance/notifications/LearningReminders.kt').read_text()
        for token in ['enqueueUniqueWork', 'ExistingWorkPolicy.APPEND_OR_REPLACE', 'cancelUniqueWork', 'ReminderPolicy.eligible', 'dueReviews(now)', 'it.targetLanguage == settings.targetLanguage', 'FLAG_IMMUTABLE', 'catch(_: SecurityException)']:
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
        self.assertIn('if(resource == null) null else withContext', source)
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

    def test_all_sheets_follow_active_theme_palette(self):
        ui = ASSETS / 'main/java/com/hydro/reedance/ui'
        sheet = (ui / 'ReedanceBottomSheet.kt').read_text()
        for token in ['val colors = MaterialTheme.colorScheme', 'containerColor = colors.surfaceContainerLow', 'contentColor = colors.onSurface', 'color = colors.onSurfaceVariant', 'tonalElevation = 0.dp']:
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
        self.assertIn('val catalogTask = async {', source)
        self.assertIn('PackInstaller(app).recover()', source)
        self.assertIn('content.catalog()', source)
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
        self.assertIn('require(file.length() <= 4 * 1024 * 1024)', source)
        self.assertNotIn('audio.readBytes()', source)
        speech = (ASSETS / 'main/java/com/hydro/reedance/media/Speech.kt').read_text()
        self.assertIn('suspend fun say', speech)
        self.assertIn('initialized.await()', speech)
        self.assertIn('withContext(Dispatchers.IO)', speech)
        quiz = (ASSETS / 'main/java/com/hydro/reedance/ui/Quiz.kt').read_text()
        self.assertIn('withContext(Dispatchers.Default)', quiz)
        self.assertIn('model.dueReviewCards()', quiz)

if __name__ == '__main__': unittest.main(verbosity=2)
