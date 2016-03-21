% BBC RD BRIRs
% see http://www.bbc.co.uk/rd/publications/sbsbrirqu_kemar_anechoic

brir = wavread('SBSBRIR_x0y0_LS0deg.wav');

% reverse order
brir(:,1:2:end) = brir(:,end-1:-2:0);
brir(:,2:2:end) = brir(:,end:-2:1);

% 1024 samples
data = brir(221:1244,:);

% normalize sum of both 90° convolved pink noise signals to 0.25 RMS
pink = wavread('../../Misc/pinknoise.wav');
conv_pink = conv(data(:,181), pink);
conv_pink = conv_pink + conv(data(:,182), pink);
conv_pink_power = sqrt(sum(conv_pink.^2) / length(conv_pink));
data = data / (conv_pink_power * 4);

% write file
fid = fopen('HRIRs.dat','w');
fwrite(fid,data,'float');
fclose(fid);
