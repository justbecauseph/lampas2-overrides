"""Reproduce audited Grim Kingdoms 2.0.3 zero-level component repairs (nbtlib 2.0.4)."""
import argparse, copy, gzip, hashlib, io, json, pathlib, zipfile
import nbtlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
PREFIX = 'lampas2-overrides/resource-patches/mr_grim_kingdomsloststructuresruins/2.0.3/'

def repair(node, path='', changes=None):
    if changes is None:
        changes = []
    if isinstance(node, dict):
        # Only item-stack enchantment components; DFU continues to own migration.
        if str(node.get('id', '')).startswith('minecraft:'):
            for key in ('minecraft:enchantments', 'minecraft:stored_enchantments'):
                levels = node.get('components', {}).get(key, {}).get('levels', {})
                for enchantment, level in list(levels.items()):
                    if int(level) == 0:
                        changes.append({'path': path + '/components/' + key + '/levels/' + enchantment,
                                        'item': str(node['id']), 'level': 0})
                        del levels[enchantment]
        for key, value in node.items():
            repair(value, path + '/' + key, changes)
    elif isinstance(node, list):
        for index, value in enumerate(node):
            repair(value, path + '/' + str(index), changes)
    return changes

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=pathlib.Path)
    parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    evidence = {'mod': 'mr_grim_kingdomsloststructuresruins', 'version': '2.0.3',
                'jar_sha256': hashlib.sha256(args.jar.read_bytes()).hexdigest(), 'resources': []}
    assert evidence['jar_sha256'] == 'd5f4af9dca99b6df9ad7db214df69d7ea63fb0e601eeabdcd73c304ddc9f7601', 'Unaudited JAR'
    with zipfile.ZipFile(args.jar) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
        assert metadata['id'] == evidence['mod'] and metadata['version'] == evidence['version']
        for name in sorted(archive.namelist()):
            if not name.startswith('data/grim_kingdoms/structure/') or not name.endswith('.nbt'):
                continue
            raw = archive.read(name)
            unpacked = gzip.decompress(raw)
            # Every affected entry has this component; skip legacy/unrelated resources.
            if b'minecraft:enchantments' not in unpacked and b'minecraft:stored_enchantments' not in unpacked:
                continue
            original = nbtlib.File.parse(io.BytesIO(unpacked))
            patched = copy.deepcopy(original)
            changes = repair(patched)
            if not changes:
                continue
            output = ROOT / 'src/main/resources' / (PREFIX + name)
            encoded = io.BytesIO()
            patched.write(encoded)
            replacement = gzip.compress(encoded.getvalue(), mtime=0)
            if args.write:
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_bytes(replacement)
            actual = nbtlib.File.parse(io.BytesIO(gzip.decompress(output.read_bytes())))
            # Full typed NBT equality checks every untouched value, including DataVersion.
            assert actual == patched, name
            assert not repair(copy.deepcopy(actual)), name
            evidence['resources'].append({'path': name, 'sha256': hashlib.sha256(raw).hexdigest(),
                                         'replacement_sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
                                         'removed': changes})
    target = ROOT / 'docs/evidence/grim-zero-enchantments.json'
    if args.write:
        target.write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
    else:
        assert json.loads(target.read_text()) == evidence
    print(f"Verified {len(evidence['resources'])} resources, {sum(len(r['removed']) for r in evidence['resources'])} zero-level entries removed; all other typed NBT preserved")

if __name__ == '__main__':
    main()
