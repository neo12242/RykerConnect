from pathlib import Path
import pypdfium2 as pdf
from PIL import Image,ImageChops
R=Path(__file__).resolve().parent/'validation'
for name in ['board-F','board-B','assembly-F']:
 im=pdf.PdfDocument(str(R/f'{name}.pdf'))[0].render(scale=5).to_pil().convert('RGB')
 box=ImageChops.difference(im,Image.new('RGB',im.size,'white')).getbbox();im.crop(box).save(R/f'{name}.png')
d=pdf.PdfDocument(str(R/'schematics.pdf'));[d[i].render(scale=1.5).to_pil().save(R/('schematic-'+str(i+1)+'.png')) for i in range(len(d))]
print('Rendered current board and schematic previews.')

