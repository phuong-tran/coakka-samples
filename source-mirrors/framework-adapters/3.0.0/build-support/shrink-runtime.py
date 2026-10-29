#!/usr/bin/env python3
"""Build a private Java-8 distribution using the actual ProGuard shrinker.

The public Kotlin declaration inventory is retained, not the whole connector
package. JNI lookup exceptions belong to the packaged consumer rules. Mappings
and removed-symbol reports are private evidence and never added to the JAR.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import zipfile


CANONICAL_ZIP_DATE = (1980, 1, 1, 0, 0, 0)


def quoted(path):
    return '"' + str(path.resolve()).replace('\\', '/') + '"'


def public_types(roots):
    names = set()
    declaration = re.compile(
        r'^(?:(?:public|data|sealed|enum|fun|open|abstract|value|annotation)\s+)*'
        r'(?:class|interface|object)\s+([A-Za-z_][A-Za-z0-9_]*)')
    for root in roots:
        for path in sorted(root.rglob('*.kt')):
            if 'demo' in path.parts:
                continue
            text = path.read_text(encoding='utf-8')
            package = re.search(r'^package\s+([\w.]+)\s*$', text, re.MULTILINE)
            if package is None:
                raise ValueError('Kotlin source has no package: ' + str(path))
            facade = re.search(r'^@file:JvmName\("([\w]+)"\)', text, re.MULTILINE)
            for line in text.splitlines():
                match = declaration.match(line)
                if match:
                    names.add(package[1] + '.' + match[1])
                elif re.match(r'^(?:public )?fun\s+', line):
                    names.add(package[1] + '.' + (facade[1] if facade else path.stem + 'Kt'))
    if 'coakka.v2.connector.RuntimeHandle' not in names:
        raise ValueError('RuntimeHandle missing from public API inventory')
    return sorted(names)


def _u2(value, offset):
    if offset + 2 > len(value):
        raise ValueError('truncated class file')
    return int.from_bytes(value[offset:offset + 2], 'big')


def _u4(value, offset):
    if offset + 4 > len(value):
        raise ValueError('truncated class file')
    return int.from_bytes(value[offset:offset + 4], 'big')


def strip_class_source_debug_extension(value):
    """Remove only the class-level SMAP attribute; executable bytecode is unchanged."""
    if value[:4] != b'\xca\xfe\xba\xbe' or len(value) < 10:
        raise ValueError('invalid class file')
    constant_count = _u2(value, 8)
    utf8 = {}
    offset = 10
    index = 1
    fixed_sizes = {3: 4, 4: 4, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
                   12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < constant_count:
        if offset >= len(value):
            raise ValueError('truncated constant pool')
        tag = value[offset]
        offset += 1
        if tag == 1:
            size = _u2(value, offset)
            offset += 2
            if offset + size > len(value):
                raise ValueError('truncated UTF-8 constant')
            utf8[index] = value[offset:offset + size]
            offset += size
        elif tag in (5, 6):
            if offset + 8 > len(value):
                raise ValueError('truncated wide constant')
            offset += 8
            index += 1
        elif tag in fixed_sizes:
            size = fixed_sizes[tag]
            if offset + size > len(value):
                raise ValueError('truncated constant')
            offset += size
        else:
            raise ValueError('unsupported class constant tag: ' + str(tag))
        index += 1

    if offset + 8 > len(value):
        raise ValueError('truncated class header')
    interfaces = _u2(value, offset + 6)
    offset += 8 + interfaces * 2

    def skip_members(position):
        count = _u2(value, position)
        position += 2
        for _ in range(count):
            if position + 8 > len(value):
                raise ValueError('truncated class member')
            attributes = _u2(value, position + 6)
            position += 8
            for _ in range(attributes):
                size = _u4(value, position + 2)
                position += 6 + size
                if position > len(value):
                    raise ValueError('truncated member attribute')
        return position

    offset = skip_members(offset)
    offset = skip_members(offset)
    attribute_count_offset = offset
    attribute_count = _u2(value, offset)
    offset += 2
    kept = []
    removed = 0
    for _ in range(attribute_count):
        start = offset
        name_index = _u2(value, offset)
        size = _u4(value, offset + 2)
        offset += 6 + size
        if offset > len(value):
            raise ValueError('truncated class attribute')
        if utf8.get(name_index) == b'SourceDebugExtension':
            removed += 1
        else:
            kept.append(value[start:offset])
    if offset != len(value):
        raise ValueError('unexpected trailing class bytes')
    if not removed:
        return value, 0
    result = (value[:attribute_count_offset] + struct.pack('>H', len(kept)) + b''.join(kept))
    return result, removed


def strip_jar_source_debug_extensions(path):
    temporary = path.with_name(path.name + '.without-smap')
    removed = 0
    try:
        with zipfile.ZipFile(path) as source, zipfile.ZipFile(temporary, 'w') as output:
            for entry in sorted(source.infolist(), key=lambda value: value.filename):
                content = source.read(entry)
                if not entry.is_dir() and entry.filename.endswith('.class'):
                    content, count = strip_class_source_debug_extension(content)
                    removed += count
                # ProGuard preserves its invocation time in every member. The
                # package owns a deterministic archive, not those cold build
                # timestamps or producer-host permissions.
                entry.date_time = CANONICAL_ZIP_DATE
                entry.extra = b''
                entry.comment = b''
                entry.create_system = 3
                entry.internal_attr = 0
                entry.external_attr = ((0o40755 if entry.is_dir() else 0o100644) << 16)
                if entry.is_dir():
                    entry.external_attr |= 0x10
                output.writestr(entry, content)
        temporary.replace(path)
    finally:
        if temporary.exists():
            temporary.unlink()
    return removed


def verify_contained_entry(name, value):
    """Private schema/runtime identities must not survive managed packaging."""
    forbidden = ('com/sun/jna/', 'com/google/protobuf/', 'coakka/v2/control/', 'coakka/v2/transport/')
    if ('ThirdParty' in name or name.endswith('.proto') or
            any(prefix in name for prefix in forbidden)):
        raise ValueError('unapproved dependency/schema/inventory entry: ' + name)
    if name.endswith('.class') and any(
            marker in value for prefix in forbidden
            for marker in (prefix.encode('ascii'), prefix.replace('/', '.').encode('ascii'))):
        raise ValueError('private schema/dependency reference in managed class: ' + name)


def verify_distribution(path, api, consumer_rules, source_jar=None):
    with zipfile.ZipFile(path) as jar:
        entries = jar.namelist()
        if len(entries) != len(set(entries)):
            raise ValueError('duplicate distribution JAR entry')
        for entry in entries:
            verify_contained_entry(entry, jar.read(entry) if entry.endswith('.class') else b'')
        classes = {name for name in entries if name.endswith('.class')}
        for name in classes:
            value = jar.read(name)
            if value[:4] != b'\xca\xfe\xba\xbe' or int.from_bytes(value[6:8], 'big') != 52:
                raise ValueError('distribution is not Java-8 bytecode: ' + name)
            _, source_debug_extensions = strip_class_source_debug_extension(value)
            if source_debug_extensions or b'SMAP\n' in value:
                raise ValueError('private source map remains: ' + name)
        for name in api:
            if name.replace('.', '/') + '.class' not in classes:
                raise ValueError('public API class was removed: ' + name)
        rule_entry = 'META-INF/proguard/coakka-runtime.pro'
        if rule_entry not in entries or jar.read(rule_entry) != consumer_rules.read_bytes():
            raise ValueError('exact consumer JNI rules are absent')
        if source_jar is not None:
            with zipfile.ZipFile(source_jar) as original:
                copied = {name for name in original.namelist() if name.startswith('native/') and not name.endswith('/')}
                copied.update(name for name in ('META-INF/LICENSE', 'META-INF/NOTICE',
                              'META-INF/COAKKA-NATIVE-LICENSE.md', 'META-INF/PACKAGE-LICENSE.md')
                              if name in original.namelist())
                if not copied or not any(name.startswith('native/') for name in copied):
                    raise ValueError('distribution has no explicit native pair')
                for name in copied:
                    if name not in entries or jar.read(name) != original.read(name):
                        raise ValueError('qualified native/legal resource changed during shrinking: ' + name)
        return len(classes)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('jar', 'output', 'java8-home', 'proguard-jar', 'consumer-rules', 'evidence'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--source-root', type=Path, action='append', required=True)
    parser.add_argument('--library', type=Path, action='append', default=[])
    args = parser.parse_args()
    if any(any(marker in value.name.lower() for marker in ('jna', 'protobuf')) for value in args.library):
        raise ValueError('private binding/schema runtime is not an allowed production dependency')
    runtime = args.java8_home / 'jre/lib/rt.jar'
    if not runtime.is_file():
        raise ValueError('--java8-home must contain the actual Java-8 runtime API')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.evidence.mkdir(parents=True, exist_ok=True)
    api = public_types(args.source_root)
    rules = [
        '-injars ' + quoted(args.jar), '-outjars ' + quoted(args.output),
        '-libraryjars ' + quoted(runtime),
        *('-libraryjars ' + quoted(value) for value in args.library),
        '-include ' + quoted(args.consumer_rules), '-dontoptimize',
        '-keepparameternames',
        '-keepattributes !SourceFile,!SourceDebugExtension,Exceptions,InnerClasses,Signature,*Annotation*,EnclosingMethod',
        '-keep class kotlin.Metadata { *; }',
        '-printmapping ' + quoted(args.evidence / 'mapping.txt'),
        '-printusage ' + quoted(args.evidence / 'usage.txt'),
    ]
    for name in api:
        rules.append('-keep,includedescriptorclasses class ' + name + ' { public protected *; }')
        rules.append('-keep,includedescriptorclasses class ' + name + '$* { public protected *; }')
    config = args.evidence / 'distribution.pro'
    config.write_text('\n'.join(rules) + '\n', encoding='utf-8')
    (args.evidence / 'public-types.json').write_text(json.dumps(api, indent=2) + '\n', encoding='utf-8')
    command = [str(args.java8_home / 'bin/java'), '-jar', str(args.proguard_jar), '@' + str(config)]
    with (args.evidence / 'proguard.log').open('w', encoding='utf-8') as log:
        subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=180)
    log = (args.evidence / 'proguard.log').read_text(encoding='utf-8')
    if re.search(r'Warning: Kotlin metadata errors encountered in coakka/', log):
        raise ValueError('owned Kotlin metadata could not be processed')
    stripped_source_maps = strip_jar_source_debug_extensions(args.output)
    mapping = (args.evidence / 'mapping.txt').read_text(encoding='utf-8')
    renamed = re.findall(r'^([^ ].*?) -> (.*?):$', mapping, re.MULTILINE)
    if not any(old != new for old, new in renamed):
        raise ValueError('no private implementation class was obfuscated')
    count = verify_distribution(args.output, api, args.consumer_rules, args.jar)
    receipt = {'command': command, 'classes': count, 'public_types': len(api),
               'stripped_source_debug_extensions': stripped_source_maps,
               'sha256': hashlib.sha256(args.output.read_bytes()).hexdigest(),
               'qualification': 'shrink-only; requires consumer E2E of this exact JAR'}
    (args.evidence / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(receipt, sort_keys=True))


if __name__ == '__main__':
    main()
