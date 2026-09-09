import argparse
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


ANDROID_NAME = '{http://schemas.android.com/apk/res/android}name'
PLACEHOLDER = re.compile(r'%(?:(\d+)\$)?([-#+ 0,(]*\d*(?:\.\d+)?)([a-zA-Z%])')
QUANTITIES = {'zero', 'one', 'two', 'few', 'many', 'other'}


def format_arguments(text, errors, location, formatted=True):
    if not formatted:
        return {}
    arguments = {}
    for match in PLACEHOLDER.finditer(text):
        position, flags, kind = match.groups()
        if kind in {'%', 'n'}:
            continue
        if position is None:
            errors.append(f'{location}: format arguments must have an explicit index')
            continue
        index = int(position)
        if index < 1:
            errors.append(f'{location}: format argument indexes start at 1')
        family = kind.lower()
        if index in arguments and arguments[index] != family:
            errors.append(f'{location}: conflicting types for argument {index}')
        arguments[index] = family
    return arguments


def read_resources(directory, errors):
    entries = {}
    for filename in sorted(directory.glob('*.xml')):
        try:
            root = ET.parse(filename).getroot()
        except (ET.ParseError, OSError) as error:
            errors.append(f'{filename}: {error}')
            continue
        if root.tag != 'resources':
            if filename.name == 'strings.xml':
                errors.append(f'{filename}: expected a resources root')
            continue
        for element in root:
            if element.tag not in {'string', 'plurals'}:
                if filename.name == 'strings.xml':
                    errors.append(f'{filename}: unsupported text resource type {element.tag}')
                continue
            name = element.get('name', '')
            location = f'{filename}:{name}'
            if not re.fullmatch('[a-z][a-z0-9_]*', name):
                errors.append(f'{location}: invalid resource name')
            if name in entries:
                errors.append(f'{location}: duplicate resource')
            values = {'value': ''.join(element.itertext())} if element.tag == 'string' else {}
            if element.tag == 'plurals':
                for item in element:
                    quantity = item.get('quantity')
                    if item.tag != 'item' or quantity not in QUANTITIES or quantity in values:
                        errors.append(f'{location}: invalid or duplicate plural quantity {quantity}')
                    values[quantity] = ''.join(item.itertext())
                if 'other' not in values:
                    errors.append(f'{location}: plurals require an other item')
            signatures = {
                quantity: format_arguments(value, errors, location, element.get('formatted') != 'false')
                for quantity, value in values.items()
            }
            entries[name] = (element.tag, element.get('translatable') != 'false', signatures, location)
    return entries


def directory_tag(name):
    if name.startswith('values-b+'):
        return name[len('values-b+'):].replace('+', '-')
    match = re.fullmatch(r'values-([a-z]{2,3})(?:-r([A-Z]{2}))?', name)
    if match:
        return match[1] + ('-' + match[2] if match[2] else '')
    return None


def check(root):
    resources = root / 'app/src/main/res'
    errors = []
    notices = []
    base = read_resources(resources / 'values', errors)
    if not base:
        errors.append('Default English string resources are missing')
    try:
        declarations = ET.parse(resources / 'xml/locales_config.xml').getroot()
        if declarations.tag != 'locale-config':
            errors.append('Locale declarations must have a locale-config root')
        languages = [element.get(ANDROID_NAME) for element in declarations if element.tag == 'locale']
        if 'en' not in languages or None in languages or len(set(languages)) != len(languages):
            errors.append('Locale declarations must contain en and unique, named locales')
    except (ET.ParseError, OSError) as error:
        errors.append(f'Locale declarations: {error}')
        languages = ['en']
    translated_names = {name for name, entry in base.items() if entry[1]}
    found = {'en'}
    for directory in sorted(resources.glob('values-*')):
        language = directory_tag(directory.name)
        if language is None:
            continue
        if language in {'en-XA', 'ar-XB'}:
            errors.append(f'{directory}: pseudo-locales must not be shipped as production translations')
            continue
        found.add(language)
        if language not in languages:
            errors.append(f'{directory}: language is absent from locales_config.xml')
        entries = read_resources(directory, errors)
        for name, (kind, translatable, signatures, location) in entries.items():
            if name not in base:
                errors.append(f'{location}: resource is absent from the English base')
                continue
            base_kind, base_translatable, base_signatures, _ = base[name]
            if not base_translatable:
                errors.append(f'{location}: non-translatable resource must stay in the base only')
            if kind != base_kind:
                errors.append(f'{location}: resource type differs from English')
                continue
            for quantity, signature in signatures.items():
                expected = base_signatures.get(quantity, base_signatures.get('other', {}))
                if signature != expected:
                    errors.append(f'{location}/{quantity}: arguments {signature} differ from English {expected}')
        complete = len(translated_names.intersection(entries))
        total = len(translated_names)
        notices.append(f'{language}: {complete}/{total} translated ({100 * complete / max(1,total):.1f}%)')
        missing = sorted(translated_names.difference(entries))
        if missing:
            notices.append(f'{language}: {len(missing)} missing entries fall back to English')
    for language in set(languages).difference(found):
        errors.append(f'{language}: declared language has no translation directory')
    for filename in (root / 'app/src').rglob('*.kt'):
        source = filename.read_text(encoding='utf-8')
        for kind, name in re.findall(r'\bR\.(string|plurals)\.([a-z][a-z0-9_]*)', source):
            if name not in base:
                errors.append(f'{filename}: R.{kind}.{name} has no default English resource')
            elif base[name][0] != kind:
                errors.append(f'{filename}: R.{kind}.{name} has a mismatched resource type')
    return notices, errors


def main():
    parser = argparse.ArgumentParser(description='Validate Android translation resources without an Android toolchain.')
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    arguments = parser.parse_args()
    notices, errors = check(arguments.root)
    for notice in notices:
        print(notice)
    for error in errors:
        print(f'ERROR: {error}', file=sys.stderr)
    print(f'Translation check: {len(errors)} error(s)')
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
