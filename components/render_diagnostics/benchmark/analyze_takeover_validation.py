"""Do not equate a successful Gradle launch with a successful functional experiment."""
from pathlib import Path
import hashlib
import json
import shutil
from datetime import datetime, timezone


def main():
    repo=Path(__file__).resolve().parents[3]
    validation=repo/'validations/conformance-render_takeover'
    instance=validation/'instance/1.21.1-neoforge/client'
    result=validation/'result'
    if (instance/'logs/latest.log').is_file():
        archive=result/'runs'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
        archive.mkdir(parents=True,exist_ok=False)
        for source in [*(instance/'logs').glob('*.log'),*(instance/'crash-reports').glob('*'),
                       *(instance/'screenshots').glob('takeover-*.png'),
                       *(instance/'radiance-audit').glob('*.txt')]:
            if source.is_file(): shutil.copy2(source,archive/source.name)
        if (result/'feasibility.json').is_file(): shutil.copy2(result/'feasibility.json',archive/'feasibility.json')
    else:
        # Completed fixtures are recycled after evidence is preserved (X-12).
        previous=json.loads((result/'analysis.json').read_text(encoding='utf8'))
        archive=(repo/previous['archive']).resolve()
        if not archive.is_relative_to((result/'runs').resolve()):
            raise RuntimeError('Evidence archive must belong to this validation')
    report=json.loads((archive/'feasibility.json').read_text(encoding='utf8'))
    log=(archive/'latest.log').read_text(encoding='utf8',errors='replace')
    for forbidden in ('VK_ERROR_DEVICE_LOST','MCVR fatal','[FATAL]','Failed to start the minecraft server'):
        if forbidden in log: raise RuntimeError('Functional run failed: '+forbidden)
    for required in ('TAKEOVER_FEASIBILITY world-ready','TAKEOVER_FEASIBILITY reload-complete',
                     'TAKEOVER_FEASIBILITY normal-stop','TAKEOVER_OFFSCREEN boundary=initial',
                     'TAKEOVER_OFFSCREEN boundary=reloaded','All dimensions are saved'):
        if required not in log: raise RuntimeError('Missing actual evidence: '+required)
    if report['flywheelLoaded'] or report['meshBuilds']!=2 or report['instanceSubmissions']<2:
        raise RuntimeError('Independent resource/instance contract not exercised')
    for flag in ('initialStateAndOffscreen','sourcePoseObserved','actualPartComparison','actualBakedComparison','reloadComplete','offscreenGpuReadback'):
        if report.get(flag) is not True: raise RuntimeError('Missing functional assertion: '+flag)
    from PIL import Image
    captures={}
    for name in ('takeover-before.png','takeover-after.png'):
        with Image.open(archive/name) as image:
            if image.size!=(1920,1080): raise RuntimeError('Wrong capture dimensions')
            pixels=image.convert('RGB')
            red=green=0
            # Only the controlled central panels, excluding HUD and fixture entities below.
            for y in range(450,630):
                for x in range(740,1180):
                    r,g,b=pixels.getpixel((x,y))
                    red+=r>100 and r>g*2 and r>b*2
                    green+=g>100 and g>r*2 and g>b*2
            if red<100 or green<100:
                raise RuntimeError(f'Controlled PT panels missing in {name}: red={red} green={green}')
            captures[name]={'redPixels':red,'greenPixels':green,'sha256':hashlib.sha256((archive/name).read_bytes()).hexdigest()}
    report['controlledPtPanels']=captures
    report['logSha256']=hashlib.sha256((archive/'latest.log').read_bytes()).hexdigest()
    report['archive']=str(archive.relative_to(repo))
    report['performanceMeasured']=False
    report['completeTakeoverImplemented']=False
    (result/'analysis.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print(json.dumps(report,indent=2))


if __name__=='__main__': main()
