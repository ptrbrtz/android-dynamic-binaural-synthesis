#ifndef KISS_FASTFIR_H
#define KISS_FASTFIR_H

#include "kiss_fft.h"
#include "_kiss_fft_guts.h"

#ifdef __cplusplus
extern "C" {
#endif

/*
 Some definitions that allow real or complex filtering
*/
#ifdef REAL_FASTFIR
// TODO use MIN_FFT_LEN?
//#define MIN_FFT_LEN 2048
//#define MIN_FFT_LEN 1024
#include "kiss_fftr.h"
typedef kiss_fft_scalar kffsamp_t;
typedef kiss_fftr_cfg kfcfg_t;
#define FFT_ALLOC kiss_fftr_alloc
#define FFTFWD kiss_fftr
#define FFTINV kiss_fftri
#else
#define MIN_FFT_LEN 1024
typedef kiss_fft_cpx kffsamp_t;
typedef kiss_fft_cfg kfcfg_t;
#define FFT_ALLOC kiss_fft_alloc
#define FFTFWD kiss_fft
#define FFTINV kiss_fft
#endif


struct kiss_fastfir_state{
    size_t nfft;
    size_t ngood;
    kfcfg_t fftcfg;
    kfcfg_t ifftcfg;
    kiss_fft_cpx * fir_freq_resp;
    size_t current_fir_freq_resp;
    kiss_fft_cpx * freqbuf;
    size_t n_freq_bins;
    kffsamp_t * tmpbuf;
};

typedef struct kiss_fastfir_state *kiss_fastfir_cfg;

kiss_fastfir_cfg kiss_fastfir_alloc(
        const kffsamp_t *imp_resp, size_t num_imp_resp, size_t num_imp_resp_frames,
        size_t *pnfft, /* if <= 0, an appropriate size will be chosen */
        void * mem,size_t *lenmem);

void fastconv1buf(const kiss_fastfir_cfg st,const kffsamp_t * in,kffsamp_t * out);


#ifdef __cplusplus
}
#endif
#endif
