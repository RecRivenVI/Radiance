"""Compare fence-retired Levitite GPU output with the retained source equations."""
from pathlib import Path
import argparse
import io
import json
import math
import struct
import tomllib
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]


def add(a, b): return tuple(x+y for x, y in zip(a, b))
def sub(a, b): return tuple(x-y for x, y in zip(a, b))
def mul(a, b): return tuple(x*b for x in a)
def dot(a, b): return sum(x*y for x, y in zip(a, b))
def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
def length(v): return math.sqrt(dot(v, v))
def mix(a, b, t): return add(mul(a, 1-t), mul(b, t))
def bilerp(p, u, v): return mix(mix(p[0], p[3], v), mix(p[1], p[2], v), u)


def noise_pixels():
    source = Path(tomllib.loads((ROOT/'local.toml').read_text(encoding='utf8'))['inputs']['aeronautics_bundled'])
    target = 'assets/aeronautics/textures/special/noise_composed.png'
    def find(data):
        with zipfile.ZipFile(data) as archive:
            if target in archive.namelist(): return archive.read(target)
            for name in archive.namelist():
                if name.endswith('.jar'):
                    value = find(io.BytesIO(archive.read(name)))
                    if value: return value
    image = Image.open(io.BytesIO(find(source))).convert('RGB')
    return image


def verify(case):
    image = noise_pixels()
    width, height = image.size
    def sample(u, v):
        x, y = (u-math.floor(u))*width-.5, (v-math.floor(v))*height-.5
        ix, iy = math.floor(x), math.floor(y)
        def pixel(x, y): return tuple(c/255 for c in image.getpixel((x % width, y % height)))
        return mix(mix(pixel(ix, iy), pixel(ix+1, iy), x-ix),
                   mix(pixel(ix, iy+1), pixel(ix+1, iy+1), x-ix), y-iy)

    checked = vertices_total = 0
    maximum = maximum_alpha = 0
    counts, signs, velocities = set(), set(), set()
    with zipfile.ZipFile(case/'client-evidence.zip') as archive:
        for name in archive.namelist():
            if not name.startswith('computed-surface-audit/') or not name.endswith('.bin'): continue
            data = archive.read(name)
            magic, vertices, indices, param_bytes, source_bytes, contact_bytes, frame, frame_high = struct.unpack_from('<8I', data)
            if magic != 0x31534643 or param_bytes != 256: raise AssertionError('GPU audit ABI')
            at = 32
            p = [struct.unpack_from('<4f', data, at+i*16) for i in range(16)]
            at += param_bytes
            source = memoryview(data)[at:at+source_bytes]
            at += source_bytes
            contacts = struct.unpack_from('<'+'I'*(contact_bytes//4), data, at)
            at += contact_bytes
            if len(data) != at+vertices*96+indices*4: raise AssertionError('Incomplete GPU readback')
            positions = [struct.unpack_from('<3f', data, at+i*16) for i in range(vertices)]
            mats = at+vertices*16
            gpu_indices = struct.unpack_from('<'+'I'*indices, data, mats+vertices*80)
            def rotate(v):
                q = p[6]
                return add(v, mul(cross(q[:3], add(cross(q[:3], v), mul(v, q[3]))), 2))
            def velocity(v): return sub(p[2][:3], cross(v, p[3][:3]))
            def fullness(v): return (1-math.exp(-2*length(velocity(v))/p[2][3]))*p[3][3]
            def drag(v):
                x2 = dot(v, v)/(p[2][3]**2)
                q = math.exp(-1.5*x2)
                def quadratic(first):
                    m = [p[first+i][:3] for i in range(3)]
                    transformed = tuple(sum(m[c][r]*v[c] for c in range(3)) for r in range(3))
                    return dot(v, transformed)
                fast, slow = quadratic(10), quadratic(7)
                peak = -.446260320297*(slow-fast)+fast
                return (q, 1-q, max(((1-3*x2)*q*(slow-fast)+fast)/peak if peak > 0 else 0, 0))
            def noise(v, t, scale):
                x, y, z = v
                a = (x+.4*y-.3*z, .2*x+y-.2*z, -.1*x-.5*y+z)
                b = (x+.2*y-.1*z, .4*x+y-.5*z, -.3*x-.2*y+z)
                def pair(a, b): return sub(sample(2.5*a+.2*t, .3*a+1.5*t), sample(1.5*b+.2*t, .1*b-2.5*t))
                return (dot(pair(a[2], b[1]), scale), dot(pair(a[0], b[2]), scale), dot(pair(a[1], b[0]), scale))
            def control(v):
                speed = length(velocity(v))
                return add(v, mul(velocity(v), (1-math.exp(-speed/p[2][3]))/(speed+.001)*p[4][3]*.15))
            def deform(v):
                speed = length(velocity(v))
                d = drag(velocity(v))
                n = noise(mul(v, .02), p[0][3]/200, (d[1]*.4, d[0]*.3, d[2]*.2))
                if speed > .0001:
                    parallel = mul(velocity(v), dot(n, velocity(v))/(speed*speed))
                    n = mix(parallel, n, 1-.9*(1-math.exp(-speed/p[2][3]))**2)
                return add(v, mul(n, fullness(v)))
            def clearance(grid, point):
                point = sub(point, p[14+grid][:3])
                cell = tuple(math.floor(x) for x in point)
                first, nx, ny, nz, boxes = contacts[grid*8:grid*8+5]
                distance = .25
                for dz in range(-1, 2):
                    for dy in range(-1, 2):
                        for dx in range(-1, 2):
                            x, y, z = add(cell, (dx, dy, dz))
                            if x < 0 or y < 0 or z < 0 or x >= nx or y >= ny or z >= nz: continue
                            i = first+int(x+nx*(y+ny*z))*2
                            start, count = contacts[i:i+2]
                            for box in range(start, start+count):
                                values = contacts[boxes+box*8:boxes+box*8+8]
                                floats = struct.unpack('<8f', struct.pack('<8I', *values))
                                delta = tuple(max(floats[c]-point[c], point[c]-floats[c+4], 0) for c in range(3))
                                distance = min(distance, length(delta))
                t = max(0, min(1, (distance+.125)/.375))
                return t*t*(3-2*t)
            for quad in range(vertices//64):
                corners = [add(struct.unpack_from('<3f', source, (quad*4+i)*32), p[0][:3]) for i in range(4)]
                controls = [control(v) for v in corners]
                nodes, alphas = [], []
                for y in range(5):
                    for x in range(5):
                        local = bilerp(controls, x/4, y/4)
                        v = deform(local)
                        world = rotate(tuple(v[i]*p[5][i] for i in range(3)))
                        nodes.append(world)
                        alphas.append(fullness(local)*.3*min(clearance(0, v), clearance(1, world)))
                for lane in range(64):
                    cell, corner = lane//4, lane % 4
                    x, y = cell % 4, cell//4
                    index = x+(1 if corner in (1, 2) else 0)+(y+(1 if corner >= 2 else 0))*5
                    actual = positions[quad*64+lane]
                    error = max(abs(a-b) for a, b in zip(actual, nodes[index]))
                    alpha = struct.unpack_from('<f', data, mats+(quad*64+lane)*80+28)[0]
                    # The source's bilinear GPU texture filtering may quantize weights to 8 bits.
                    maximum = max(maximum, error)
                    maximum_alpha = max(maximum_alpha, abs(alpha-alphas[index]))
                    if not all(math.isfinite(v) for v in actual) or error > .004 or abs(alpha-alphas[index]) > .002:
                        raise AssertionError(f'{name} quad={quad} lane={lane} positionError={error} alphaError={abs(alpha-alphas[index])}')
                pattern = (0, 1, 2, 2, 3, 0)
                for i in range(96):
                    if gpu_indices[quad*96+i] != quad*64+(i//6)*4+pattern[i % 6]: raise AssertionError('GPU index topology')
                for triangle in range(32):
                    ids = gpu_indices[quad*96+triangle*3:quad*96+triangle*3+3]
                    a, b, c = (positions[i] for i in ids)
                    if length(cross(sub(b, a), sub(c, a))) < 1e-8: raise AssertionError('Collapsed GPU shell')
            counts.add(vertices)
            signs.add(int(p[4][3]))
            velocities.add(1 if p[2][0] > 0 else -1)
            vertices_total += vertices
            checked += 1
    if not {384, 640, 896}.issubset(counts) or signs != {-1, 1} or velocities != {-1, 1}:
        raise AssertionError(f'Insufficient GPU coverage: counts={counts}, signs={signs}, velocities={velocities}')
    field_nodes = 0
    maximum_field = 0
    for path in sorted(case.glob('geometry-*.json')):
        for record in json.loads(path.read_text(encoding='utf8')):
            stem = record.get('fieldReadback')
            if not stem: continue
            p = list(struct.iter_unpack('<4f', (case/(stem+'-params.bin')).read_bytes()))
            source = (case/(stem+'-source.bin')).read_bytes()
            values = [v[0] for v in struct.iter_unpack('<f', (case/(stem+'-field.bin')).read_bytes())]
            for quad in range(record['count']//4):
                corners = [add(struct.unpack_from('<3f', source, (quad*4+i)*128), p[0][:3]) for i in range(4)]
                for y in range(5):
                    for x in range(5):
                        local = bilerp(corners, x/4, y/4)
                        n = noise(mul(local, .1), p[0][3]/500, (.7, 0, 0))
                        if p[3][3] > 0:
                            d = drag(velocity(local)) if p[1][3] else (1, 0, 0)
                            global_point = add(rotate(local), p[1][:3])
                            n = mix(n, noise(mul(global_point, .1), p[0][3]/500, (d[1]*.7, d[0], d[2])), p[3][3])
                        expected = (abs(sum(n))*max(.3, fullness(local)), fullness(local))
                        node = quad*25+y*5+x
                        error = max(abs(values[node*2+i]-expected[i]) for i in range(2))
                        if not math.isfinite(error) or error > .004:
                            raise AssertionError(f'{stem}: body colour node {node} error={error}')
                        maximum_field = max(maximum_field, error)
                        field_nodes += 1
    result = dict(passed=True, captures=checked, vertices=vertices_total, vertexCounts=sorted(counts),
                  maxPositionError=maximum, maxAlphaError=maximum_alpha, shapes='one/two/three blocks',
                  bodyFieldNodes=field_nodes, maxBodyFieldError=maximum_field,
                  scope='Real GPU output vs source equations; not visual acceptance or performance')
    (case/'computed-surface-validation.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf8')
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('case', type=Path)
    print(json.dumps(verify(parser.parse_args().case), indent=2))
