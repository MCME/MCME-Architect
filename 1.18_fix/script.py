import pathlib
import glob

path = pathlib.Path().resolve()
all_files = glob.glob(str(path) + "/*.reg")

for file in all_files:
    f = open(file,"r")
    f = f.read()
    if f.find("minY") != -1:
        start = f.find("minY")
        end = f[start:].find('\n') + start
        temp = str(int(f[start+5:end]) - 64)
        f = f[:start+6] + temp + f[end:]
    
        start = f.find("maxY")
        end = f[start:].find('\n') + start
        temp = str(int(f[start+5:end]) + 64)
        f = f[:start+6] + temp + f[end:]
    elif f.find("minimumPoint") != -1:
        start = f.find("minimumPoint")
        komma1 = f[start:].find(',') + start
        komma2 = f[komma1+1:].find(',') + komma1
        temp = str(int(f[komma1+1:komma2+1]) - 64)
        f = f[:komma1+1] + temp + f[komma2+1:]
    
        start = f.find("maximumPoint")
        komma1 = f[start:].find(',') + start
        komma2 = f[komma1+1:].find(',') + komma1
        temp = str(int(f[komma1+1:komma2+1]) - 64)
        f = f[:komma1+1] + temp + f[komma2+1:]
    with open(file,"w") as g:
        g.write(f)