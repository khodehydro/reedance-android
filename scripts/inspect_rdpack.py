"""Bounded RDPACK1 transport/header inspection. NOT decryption or publisher verification.
Usage: python scripts/inspect_rdpack.py /path/to/v000001-<sha256>.rdpack
No network, credentials, key discovery or archive extraction is performed.
"""
import hashlib
import json
import pathlib
import re
import struct
import sys

MAX_BYTES = 256 * 1024 * 1024


def inspect_pack(path):
    path = pathlib.Path(path)
    name = re.fullmatch(r'v([0-9]{6,10})-([a-f0-9]{64})\.rdpack', path.name)
    if not name:
        raise ValueError('Expected a canonical version/hash package filename')
    version, expected_hash = int(name[1]), name[2]
    if not 1 <= version <= 2147483647 or path.name != f'v{version:06d}-{expected_hash}.rdpack':
        raise ValueError('Invalid/noncanonical content version')
    size = path.stat().st_size
    if not 50 <= size <= MAX_BYTES:
        raise ValueError('Package size outside v1 limits')
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        prefix = stream.read(61)  # Maximum v1 header: 29 + 32 bytes.
        digest.update(prefix)
        total = len(prefix)
        while chunk := stream.read(64 * 1024):
            total += len(chunk)
            if total > MAX_BYTES:
                raise ValueError('Stream exceeded package limit')
            digest.update(chunk)
    if total != size:
        raise ValueError('File changed during inspection')
    actual_hash = digest.hexdigest()
    if actual_hash != expected_hash:
        raise ValueError('SHA-256 differs from filename (filename is not a signed manifest)')
    if prefix[:8] != b'RDPACK1\x00':
        raise ValueError('Invalid RDPACK1 magic')
    length = prefix[8]
    if not 1 <= length <= 32:
        raise ValueError('Invalid encryption key ID length')
    header_length = 29 + length
    if len(prefix) < header_length or size < header_length + 16:
        raise ValueError('Truncated header/tag')
    key_id = prefix[9:9 + length].decode('ascii', errors='strict')
    if not re.fullmatch(r'enc-[a-z0-9-]{1,28}', key_id):
        raise ValueError('Invalid encryption key ID')
    plain_length = struct.unpack('>Q', prefix[21 + length:29 + length])[0]
    if not 0 < plain_length <= MAX_BYTES:
        raise ValueError('Plaintext length outside v1 limits')
    if size != header_length + plain_length + 16:
        raise ValueError('Container length differs from header + plaintext + GCM tag')
    return {
        'file_name': path.name,
        'size_bytes': size,
        'sha256': actual_hash,
        'content_version_code': version,
        'container': 'RDPACK1',
        'encryption': 'AES-256-GCM',
        'encryption_key_id': key_id,
        'header_bytes': header_length,
        'plaintext_zip_bytes': plain_length,
        'tag_bytes': 16,
        'checks_passed': ['filename_hash', 'magic', 'key_id_syntax', 'bounded_length'],
        'publisher_signature': 'not_verified_requires_signed_manifest_and_trusted_public_key',
        'gcm_authentication': 'not_verified_requires_matching_distribution_key',
        'archive_extraction': 'not_attempted',
        'android_install_or_playback': 'not_tested',
    }


if __name__ == '__main__':
    try:
        if len(sys.argv) != 2:
            raise ValueError(__doc__.strip())
        print(json.dumps(inspect_pack(sys.argv[1]), indent=2, ensure_ascii=False))
    except (ValueError, OSError) as error:
        print(f'Inspection failed: {error}', file=sys.stderr)
        sys.exit(1)
