/*
Copyright (c) 2016 Peter Bartz

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

#ifndef __BLOCKINGBYTEBUFFERQUEUE_H__
#define __BLOCKINGBYTEBUFFERQUEUE_H__

#include <deque>
#include <pthread.h>

using namespace std;

class BlockingByteBufferQueue {
public:
	BlockingByteBufferQueue(int maxNumElems) {
		this->maxNumElems = maxNumElems;
		this->currNumElems = 0;

		pthread_mutex_init(&mutex, NULL);
		pthread_cond_init(&notFullCond, NULL);
		pthread_cond_init(&notEmptyCond, NULL);
	}

	~BlockingByteBufferQueue() {
		pthread_mutex_destroy(&mutex);
		pthread_cond_destroy(&notFullCond);
		pthread_cond_destroy(&notEmptyCond);
	}

	void put(char *buf) {
		pthread_mutex_lock(&mutex);

		// make sure queue is not full
		while (currNumElems >= maxNumElems)
			pthread_cond_wait(&notFullCond, &mutex);

		bufQueue.push_back(buf);
		currNumElems++;

		pthread_cond_signal(&notEmptyCond);	// wake up "takers"
		pthread_mutex_unlock(&mutex);
	}

	// put, even if queue is "full"
	void putSpecial(char *buf) {
		pthread_mutex_lock(&mutex);

		bufQueue.push_back(buf);
		currNumElems++;

		pthread_cond_signal(&notEmptyCond);	// wake up "takers"
		pthread_mutex_unlock(&mutex);
	}

	// put, even if queue is "full"
	void putSpecialFront(char *buf) {
		pthread_mutex_lock(&mutex);

		bufQueue.push_front(buf);
		currNumElems++;

		pthread_cond_signal(&notEmptyCond);	// wake up "takers"
		pthread_mutex_unlock(&mutex);
	}

	char* take() {
		char *buf = NULL;

		pthread_mutex_lock(&mutex);

		// make sure there is something available
		while (currNumElems <= 0)
			pthread_cond_wait(&notEmptyCond, &mutex);

		buf = bufQueue.front();
		bufQueue.pop_front();
		currNumElems--;

		pthread_cond_signal(&notFullCond);	// wake up "putters"
		pthread_mutex_unlock(&mutex);

		return buf;
	}

	int getNumElems() {
		return currNumElems;	// should be atomic enough not to need locking
	}

private:
	deque<char*> bufQueue;
	int currNumElems;
	int maxNumElems;
	pthread_mutex_t mutex;
	pthread_cond_t notFullCond;
	pthread_cond_t notEmptyCond;
};

#endif /* __BLOCKINGBYTEBUFFERQUEUE_H__ */
