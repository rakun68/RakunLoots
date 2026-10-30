from decimal import Decimal, localcontext
from pathlib import Path

def chance(light, heavy, count, precision):
    with localcontext() as ctx:
        ctx.prec=precision
        dp={0:Decimal(1)}
        for drawn in range(count):
            nxt={}
            for selected_light, probability in dp.items():
                selected_heavy=drawn-selected_light
                left_light=light-selected_light
                left_heavy=heavy-selected_heavy
                total=left_light+2*left_heavy
                if left_light:
                    nxt[selected_light+1]=nxt.get(selected_light+1,Decimal(0))+probability*left_light/total
                if left_heavy:
                    nxt[selected_light]=nxt.get(selected_light,Decimal(0))+probability*(2*left_heavy)/total
            dp=nxt
        expected_light=sum((Decimal(i)*p for i,p in dp.items()),Decimal(0))
        return expected_light/light, (Decimal(count)-expected_light)/heavy, sum(dp.values())

with localcontext() as ctx:
    ctx.prec=100
    assert abs(chance(1,1,1,100)[0]-Decimal(1)/3)<Decimal('1e-95')
    assert chance(1,1,2,100)[:2]==(Decimal(1),Decimal(1))
rows=[]
for precision in (70,100):
    light,heavy,mass=chance(250,250,250,precision)
    rows.append(f'precision={precision}\nlight={light}\nheavy={heavy}\nprobability_mass={mass}\n')
output='\n'.join(rows)
print(output)
Path(r'C:\Workspace\Tools\Boxie\runs\rakunloots\phase9-large-chance-host\positive-transition-reference.txt').write_text(output,encoding='utf-8')
