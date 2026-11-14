"""Host tests for transport/header checks; synthetic bytes are NOT authenticated packs."""
import hashlib
import pathlib
import struct
import tempfile
import unittest
from inspect_rdpack import inspect_pack, MAX_BYTES

ROOT = pathlib.Path(__file__).resolve().parents[1]


class PackInspectionTests(unittest.TestCase):
    def pack(self, key=b'enc-host-test', plain_length=8, magic=b'RDPACK1\x00'):
        return magic + bytes([len(key)]) + key + b'\x00' * 12 + struct.pack('>Q', plain_length) + b'\x00' * 8 + b'\x00' * 16

    def inspect_bytes(self, data, digest=None):
        with tempfile.TemporaryDirectory() as folder:
            path = pathlib.Path(folder) / f'v000001-{digest or hashlib.sha256(data).hexdigest()}.rdpack'
            path.write_bytes(data)
            return inspect_pack(path)

    def test_existing_golden_fixture_header(self):
        path = next((ROOT / 'contracts/v1/fixtures').glob('*.rdpack'))
        result = inspect_pack(path)
        self.assertEqual('RDPACK1', result['container'])
        self.assertEqual('not_attempted', result['archive_extraction'])

    def test_header_success_never_claims_authentication(self):
        result = self.inspect_bytes(self.pack())
        self.assertEqual(42, result['header_bytes'])
        self.assertEqual('not_tested', result['android_install_or_playback'])
        self.assertTrue(result['gcm_authentication'].startswith('not_verified'))

    def test_wrong_hash(self):
        with self.assertRaises(ValueError): self.inspect_bytes(self.pack(), 'a' * 64)

    def test_wrong_magic(self):
        with self.assertRaises(ValueError): self.inspect_bytes(self.pack(magic=b'NOTPACK!'))

    def test_invalid_key_identifier(self):
        with self.assertRaises(ValueError): self.inspect_bytes(self.pack(key=b'enc-../escape'))

    def test_length_mismatch(self):
        with self.assertRaises(ValueError): self.inspect_bytes(self.pack(plain_length=9))

    def test_zero_and_oversized_plaintext(self):
        for size in [0, MAX_BYTES + 1]:
            with self.subTest(size=size), self.assertRaises(ValueError): self.inspect_bytes(self.pack(plain_length=size))

    def test_truncated_header(self):
        with self.assertRaises(ValueError): self.inspect_bytes(self.pack()[:45])


if __name__ == '__main__': unittest.main(verbosity=2)
