% QU KEMAR HRTF set, see
% http://twoears.aipa.tu-berlin.de/doc/latest/database/impulse-responses/#anechoic-hrtfs-from-the-kemar-manikin-with-different-distances

% load original data
SOFAstart;
hrtf = SOFAload('QU_KEMAR_anechoic_3m.sofa');

% reorder
data = zeros(256,720);
data(:,1:2:720) = permute(circshift(squeeze(hrtf.Data.IR(:,1,50:305)),180),[2 1]);
data(:,2:2:720) = permute(circshift(squeeze(hrtf.Data.IR(:,2,50:305)),180),[2 1]);

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
