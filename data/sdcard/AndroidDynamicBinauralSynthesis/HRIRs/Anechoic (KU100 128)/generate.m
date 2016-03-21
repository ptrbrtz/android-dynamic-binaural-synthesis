% Neumann HRTF set, see
% http://twoears.aipa.tu-berlin.de/doc/latest/database/impulse-responses/#spherical-far-field-hrtf-compilation-of-the-neumann-ku100

% load original data
SOFAstart;
hrtf = SOFAload('HRIR_CIRC360.sofa');

% reorder
data = zeros(128,720);
data(:,1:2:720) = permute(squeeze(hrtf.Data.IR(:,1,:)),[2 1]);
data(:,2:2:720) = permute(squeeze(hrtf.Data.IR(:,2,:)),[2 1]);

% normalize sum of both 90° convolved pink noise signals to 0.25 RMS
pink = wavread('../../Misc/pinknoise.wav');
conv_pink = conv(data(:,181), pink);
conv_pink = conv_pink + conv(data(:,182), pink);
conv_pink_power = sqrt(sum(conv_pink.^2) / length(conv_pink));
%conv_pink_power = sqrt(sum((data(:,181) + data(:,182)).^2)) / 4
%conv_pink_power = max(max(abs(data))) * 4

data = data / (conv_pink_power * 4);

% write file
fid = fopen('HRIRs.dat','w');
fwrite(fid,data,'float');
fclose(fid);
