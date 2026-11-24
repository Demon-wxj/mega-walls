"""Rebuild the 1.18.45 maintenance overlay from an exact local 1.18.41 JAR."""
from argparse import ArgumentParser
from hashlib import file_digest
from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZIP_DEFLATED, ZipFile
import json
import os
import shutil
import subprocess


ROOT = Path(__file__).resolve().parent
VERSION = '1.18.45-bed-items-mc1.21.1'
BASELINE_SHA256 = '15eceba2c7476e54372846fbe80ce621fe22e20db5ca7215b7225ce5b36dbaf9'


def digest(path):
    with path.open('rb') as stream:
        return file_digest(stream, 'sha256').hexdigest()


def run(command, **kwargs):
    subprocess.run([str(item) for item in command], check=True, **kwargs)


def commands(args, workspace):
    suffix = '.exe' if os.name == 'nt' else ''
    java = args.java_home / 'bin' / ('java' + suffix)
    javac = args.java_home / 'bin' / ('javac' + suffix)
    remapper = os.pathsep.join(map(str, args.remapper_jar))

    def compile_java(sources, classes, classpath, label):
        classes.mkdir(exist_ok=True)
        arguments = [
            '--release', '21', '-encoding', 'UTF-8', '-proc:none',
            '-cp', os.pathsep.join(map(str, classpath)), '-d', classes, *sources,
        ]
        argfile = workspace / f'{label}.args'
        argfile.write_text('\n'.join('"' + str(item).replace('\\', '/') + '"' for item in arguments), encoding='utf-8')
        run([javac, '@' + str(argfile)])

    def remap(source, target, from_namespace, to_namespace, *classpath):
        run([java, '-Xmx2G', '-cp', remapper, 'net.fabricmc.tinyremapper.Main',
             source, target, args.mappings, from_namespace, to_namespace, *classpath])

    return java, remapper, compile_java, remap


def collect_fabric_api(api, aggregate):
    with ZipFile(api) as source, ZipFile(aggregate, 'w') as destination:
        for module in source.namelist():
            if not module.endswith('.jar'):
                continue
            with ZipFile(BytesIO(source.read(module))) as jar:
                for entry in jar.namelist():
                    if entry.endswith('.class') and entry not in destination.NameToInfo:
                        destination.writestr(entry, jar.read(entry))


def overlay_sources(reference):
    files = (ROOT / 'recovered-files.txt').read_text(encoding='utf-8').splitlines()
    recovered = [reference / name for name in files]
    for path in recovered:
        # Apply the checked-in patch to LF text on every platform.
        path.write_text(path.read_text(encoding='utf-8-sig'), encoding='utf-8', newline='\n')
    patch = ROOT / 'baseline-1.18.41.patch'
    run(['git', 'apply', '--check', '--unidiff-zero', patch], cwd=reference)
    run(['git', 'apply', '--unidiff-zero', patch], cwd=reference)
    authored = list((ROOT / 'src/main/java').rglob('*.java'))
    return sorted(recovered + authored, key=lambda path: path.as_posix().split('/com/', 1)[-1])


def write_release(baseline, patch, output):
    resources = ROOT / 'src/main/resources'
    with ZipFile(baseline) as original, ZipFile(patch) as changed, ZipFile(output, 'x', ZIP_DEFLATED) as jar:
        updated = set(changed.namelist())
        for entry in original.namelist():
            if entry not in updated and entry != 'fabric.mod.json':
                jar.writestr(entry, original.read(entry))
        for entry in changed.namelist():
            jar.writestr(entry, changed.read(entry))
        for resource in sorted(resources.rglob('*')):
            if resource.is_file():
                jar.write(resource, resource.relative_to(resources).as_posix())
        metadata = json.loads(original.read('fabric.mod.json'))
        metadata['version'] = VERSION
        metadata['mixins'].append('megawalls-bed-utilities.mixins.json')
        metadata['entrypoints']['main'].append('com.megawalls.maintenance.MaintenanceMod')
        metadata['entrypoints']['client'].append('com.megawalls.maintenance.client.MaintenanceClient')
        jar.writestr('fabric.mod.json', json.dumps(metadata, ensure_ascii=False, indent=2))


def build(args, workspace, output):
    java, remapper, compile_java, remap = commands(args, workspace)
    baseline_named = workspace / 'baseline-named.jar'
    remap(args.baseline, baseline_named, 'intermediary', 'named', args.minecraft_intermediary)
    reference = workspace / 'reference'
    run([java, '-Xmx3G', '-jar', args.cfr, baseline_named, '--outputdir', reference,
         '--extraclasspath', args.minecraft_named, '--silent', 'true'])
    sources = overlay_sources(reference)

    api_intermediary = workspace / 'fabric-api-intermediary.jar'
    api_named = workspace / 'fabric-api-named.jar'
    collect_fabric_api(args.fabric_api, api_intermediary)
    remap(api_intermediary, api_named, 'intermediary', 'named', args.minecraft_intermediary)
    libraries = [jar for folder in args.library_dir for jar in folder.rglob('*.jar')]
    classes = workspace / 'classes'
    compile_java(sources, classes, [baseline_named, args.minecraft_named, api_named, *libraries], 'maintenance')

    patchers = ['VisualPatcher', 'ArenaRulesPatcher']
    patcher_classes = workspace / 'patchers'
    compile_java([ROOT / 'tools' / (name + '.java') for name in patchers], patcher_classes,
                 args.remapper_jar, 'patchers')
    for name in patchers:
        run([java, '-cp', os.pathsep.join([str(patcher_classes), remapper]), name, baseline_named, classes])

    patch_named = workspace / 'patch-named.jar'
    patch_intermediary = workspace / 'patch-intermediary.jar'
    with ZipFile(patch_named, 'w', ZIP_DEFLATED) as jar:
        for path in classes.rglob('*.class'):
            jar.write(path, path.relative_to(classes).as_posix())
    remap(patch_named, patch_intermediary, 'named', 'intermediary', args.minecraft_named, baseline_named, api_named)
    write_release(args.baseline, patch_intermediary, output)


def main():
    parser = ArgumentParser(description=__doc__)
    for name in ['baseline', 'java-home', 'cfr', 'minecraft-named', 'minecraft-intermediary', 'mappings', 'fabric-api']:
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--library-dir', type=Path, action='append', required=True)
    parser.add_argument('--remapper-jar', type=Path, action='append', required=True)
    parser.add_argument('--output-dir', type=Path, default=ROOT / 'output')
    args = parser.parse_args()
    if digest(args.baseline) != BASELINE_SHA256:
        parser.error('the provided baseline JAR does not match the exact 1.18.41 release SHA-256')
    args.output_dir.mkdir(parents=True, exist_ok=True)
    output = args.output_dir / f'megawalls-mod-{VERSION}.jar'
    if output.exists():
        parser.error(f'output already exists: {output}')
    with TemporaryDirectory(prefix='megawalls-maintenance-') as temporary:
        staging = Path(temporary) / output.name
        build(args, Path(temporary), staging)
        # A failed build leaves the final location untouched.
        with staging.open('rb') as source, output.open('xb') as target:
            shutil.copyfileobj(source, target)
    print(f'{output.name}  SHA-256 {digest(output)}')


if __name__ == '__main__':
    main()
