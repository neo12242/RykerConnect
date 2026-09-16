from pathlib import Path
import pypdfium2 as pdf
from PIL import Image,ImageChops
R=Path(__file__).resolve().parent
for v in ['12V','USB']:
 d=R/('REV05-'+v)/'validation'
 for name in ['board-F','board-B','assembly-F']:
  im=pdf.PdfDocument(str(d/(name+'.pdf')))[0].render(scale=5).to_pil().convert('RGB');box=ImageChops.difference(im,Image.new('RGB',im.size,'white')).getbbox();im.crop(box).save(d/(name+'.png'))
 doc=pdf.PdfDocument(str(d/'schematics.pdf'))
 for i in range(len(doc)):doc[i].render(scale=1.6).to_pil().save(d/f'schematic-{i+1}.png')
 print(v,'drawings rendered')
