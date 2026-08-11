"""Private SDK input and diagnostic agent builds; never installs globally or into a product JAR."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tomllib
import zipfile
from prepare_loading_validation import members

ROOT = Path(__file__).resolve().parents[3]
COMPONENT = ROOT / 'components/render_diagnostics'
REQUIRED = ('LICENSE', 'include/GFSDK_Aftermath_GpuCrashDump.h',
            'include/GFSDK_Aftermath_GpuCrashDumpDecoding.h', 'lib/x64/GFSDK_Aftermath_Lib.x64.dll')


def prepare_sdk(archive, hashes, directory):
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    if digest not in hashes:
        raise ValueError('Aftermath SDK input SHA-256 mismatch')
    destination = directory / digest[:16]
    with zipfile.ZipFile(archive) as sdk:
        files = members(sdk)
        if sum(entry.file_size for entry in files) > 256 * 1024 * 1024:
            raise ValueError('SDK extraction exceeds local input budget')
        for name in REQUIRED:
            if name not in sdk.namelist():
                raise ValueError(f'SDK missing {name}')
        if destination.exists():
            for entry in files:
                if entry.is_dir():
                    continue
                local = destination / entry.filename
                if not local.is_file() or local.read_bytes() != sdk.read(entry):
                    raise ValueError('Prepared SDK changed; preserve evidence and recycle damaged cache')
        else:
            destination.mkdir(parents=True)
            for entry in files:
                local = destination / entry.filename
                if entry.is_dir():
                    local.mkdir(parents=True, exist_ok=True)
                else:
                    local.parent.mkdir(parents=True, exist_ok=True)
                    with sdk.open(entry) as source, local.open('xb') as target:
                        shutil.copyfileobj(source, target)
    return destination


def cmake_build(agent, generator):
    source = COMPONENT / 'native/aftermath'
    build = COMPONENT / 'build/native' / ('aftermath' if agent else 'capture-tests')
    args = ['cmake', '-S', str(source), '-B', str(build),
            f'-DRADIANCE_CAPTURE_AGENT={"ON" if agent else "OFF"}']
    if generator:
        args += ['-G', generator, '-A', 'x64']
    if agent:
        config = tomllib.loads((ROOT/'inputs.toml').read_text(encoding='utf-8'))['nsight_aftermath_sdk']
        local = tomllib.loads((ROOT/'local.toml').read_text(encoding='utf-8'))['inputs']
        if 'nsight_aftermath_sdk' not in local:
            raise ValueError('Configure the private nsight_aftermath_sdk input or use -PgpuDiagnostics=light')
        sdk = prepare_sdk(Path(local['nsight_aftermath_sdk']), config['sha256'],
                          COMPONENT / 'build/aftermath-sdk')
        args += [f'-DAFTERMATH_SDK={sdk}']
    subprocess.run(args, check=True)
    subprocess.run(['cmake', '--build', str(build), '--config', 'RelWithDebInfo', '--parallel', '4'], check=True)
    if not agent:
        subprocess.run(['ctest', '--test-dir', str(build), '-C', 'RelWithDebInfo', '--output-on-failure'], check=True)
    else:
        dll = build/'RelWithDebInfo/radiance-aftermath-agent.dll'
        identity = {'agent_sha256': hashlib.sha256(dll.read_bytes()).hexdigest(),
                    'sdk_dll_sha256': hashlib.sha256((sdk/REQUIRED[-1]).read_bytes()).hexdigest(),
                    'sdk_input_version': config['version'], 'sdk_input': local['nsight_aftermath_sdk'],
                    'capture': 'Vulkan; resource tracking + deferred shader debug; no automatic call stacks or extra shader faults'}
        (build/'identity.json').write_text(json.dumps(identity, indent=2)+'\n', encoding='utf-8')


def format_check():
    formatter = shutil.which('clang-format')
    if not formatter and os.name == 'nt':
        finder = Path(os.environ.get('ProgramFiles(x86)', ''))/'Microsoft Visual Studio/Installer/vswhere.exe'
        if finder.is_file():
            found = subprocess.check_output([str(finder), '-latest', '-products', '*', '-find',
                                             r'VC\Tools\Llvm\x64\bin\clang-format.exe'], text=True).splitlines()
            formatter = next((line for line in found if Path(line).is_file()), None)
    if not formatter:
        raise ValueError('Install Visual Studio C++ Clang tools or add clang-format to PATH')
    files = sorted(p for p in (COMPONENT/'native/aftermath').iterdir() if p.suffix in ('.cpp', '.hpp'))
    subprocess.run([formatter, f'--style=file:{ROOT / "components/native_renderer/.clang-format"}',
                    '--dry-run', '--Werror', *map(str, files)], check=True)


if __name__ == '__main__':
    if sys.argv[1] == 'format':
        format_check()
    else:
        cmake_build(sys.argv[1] == 'build', sys.argv[2] if len(sys.argv) > 2 else '')
