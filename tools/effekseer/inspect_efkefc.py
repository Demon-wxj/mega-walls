"""Inspect official EFKE chunks and roundtrip editor parameters; no game needed."""
from pathlib import Path
import struct, zlib, json
import xml.etree.ElementTree as ET
from xml.dom import minidom

ROOT=Path(__file__).resolve().parent

class Reader:
    def __init__(self,data): self.data=data; self.pos=0
    def read(self,n):
        b=self.data[self.pos:self.pos+n]; self.pos+=n; return b
    def n(self,fmt): return struct.unpack('<'+fmt,self.read(struct.calcsize(fmt)))[0]

def inspect(path):
    data=path.read_bytes()
    assert data[:4]==b'EFKE',path
    r=Reader(data[8:]); chunks={}
    while r.pos<len(r.data):
        key=r.read(4).decode(); size=r.n('I'); chunks[key]=r.read(size)
    r=Reader(zlib.decompress(chunks['EDIT']))
    def string_table():
        table={}
        for _ in range(r.n('h')):
            text=r.read(r.n('h')).decode('utf-8'); key=r.n('h'); table[key]=text
        return table
    keys=string_table(); values=string_table()
    def nodes(parent):
        for _ in range(r.n('h')):
            el=ET.SubElement(parent,keys[r.n('h')])
            if r.n('i'): el.text=values[r.n('h')]
            if r.n('i'): nodes(el)
    dummy=ET.Element('dummy'); nodes(dummy); project=list(dummy)[0]
    (path.with_suffix('.roundtrip.xml')).write_bytes(minidom.parseString(ET.tostring(project)).toprettyxml(indent='  ',encoding='utf-8'))
    effects=project.findall('./Root/Children/Node')
    assert len(effects)==312, len(effects)
    assert len(project.findall('.//DrawingValues/ColorAll/Fixed'))==312
    assert len(project.findall('.//Sprite/Position_Fixed_LL'))==252
    textures=set(el.text for el in project.findall('.//ColorTexture'))
    for texture in textures:
        assert (path.parent/texture).is_file(),texture
    assert all(el.text=='2' for el in project.findall('.//AlphaBlend'))
    # Max generation is finite and no geometry ignores depth.
    assert not any(el.text=='True' for el in project.findall('.//MaxGeneration/Infinite'))
    assert not any(el.text=='False' for el in project.findall('.//ZTest'))
    info={'file':path.name,'bytes':len(data),'chunks':{k:len(v) for k,v in chunks.items()},
          'top_level_nodes':len(effects),'total_nodes':len(project.findall('.//Node')),
          'textures':sorted(textures),'editor_version':project.findtext('ToolVersion'),
          'first_generation':ET.tostring(effects[0].find('CommonValues'),encoding='unicode')}
    print(json.dumps(info,indent=2))
    return info

if __name__=='__main__':
    summaries=[inspect(path) for path in sorted((ROOT/'authored').glob('*.efkefc'))]
    (ROOT/'export-verification.json').write_text(json.dumps(summaries,indent=2),encoding='utf-8')
