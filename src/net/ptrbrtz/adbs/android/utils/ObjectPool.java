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

package net.ptrbrtz.adbs.android.utils;

/**
 * A generic high performance object pooling implementation without size 
 * limit. Very useful to avoid pauses caused by the Garbage Collector.
 * Will only allocate memory if get() is called on empty pool.
 *
 * @param <T> Type of pooled objects
 */
public class ObjectPool<T> {
	// Simple linked list
	private class ListElement {
		public T item;
		public ListElement next;
		public ListElement prev;
	}
	private ListElement listHead = new ListElement();
	private ListElement listPosition = listHead;
	
	private ObjectFactory<T> factory;

	/**
	 * Creates an empty pool.
	 * 
	 * @param factory Factory to be used for creating new objects
	 */
	public ObjectPool(ObjectFactory<T> factory) {
		this.factory = factory;
	}
	
	/**
	 * Creates a pool of specified initial size. Choose sufficient
	 * size to avoid later allocations at all.
	 * 
	 * @param factory Factory to be used for creating new objects
	 * @param initialSize Initial number of objects in the pool
	 */
	public ObjectPool(ObjectFactory<T> factory, int initialSize) {
		this.factory = factory;
		for (int i = 0; i < initialSize; i++) {
			put(factory.newObject());
		}
	}
	
	/**
	 * Puts an object into the pool. Pool size is not limited.
	 * 
	 * @param item
	 */
	public synchronized void put(T item) {
		if (listPosition.next == null) {
			listPosition.next = new ListElement();
			listPosition.next.prev = listPosition;
		}
		listPosition = listPosition.next;
		listPosition.item = item;
	}
	
	/**
	 * Removes and returns an object from the pool. If there are no objects
	 * left, a new object will be created using the factory.
	 * 
	 * @return
	 */
	public synchronized T get() {
		T item;
		if (listPosition != listHead) {
			item = listPosition.item;
			listPosition = listPosition.prev;
		} else {
			item = factory.newObject();
		}
		
		return item;
	}
	
	/**
	 * Used to create objects for an object pool.
	 *
	 * @param <T> Type of pooled objects
	 */
	public static interface ObjectFactory<T> {
		public T newObject();
	}
	
	/**
	 * Object wrapper to enable pooling of primitive float values
	 */
	public static class Float {
		public float value;
	}
	/**
	 * Object wrapper to enable pooling of primitive double values
	 */
	public static class Double {
		public double value;
	}
	/**
	 * Object wrapper to enable pooling of primitive byte values
	 */
	public static class Byte {
		public byte value;
	}
	/**
	 * Object wrapper to enable pooling of primitive short values
	 */
	public static class Short {
		public short value;
	}
	/**
	 * Object wrapper to enable pooling of primitive int values
	 */
	public static class Int {
		public int value;
	}
	/**
	 * Object wrapper to enable pooling of primitive long values
	 */
	public static class Long {
		public long value;
	}
	/**
	 * Object wrapper to enable pooling of primitive boolean values
	 */
	public static class Boolean {
		public boolean value;
	}
	/**
	 * Object wrapper to enable pooling of primitive char values
	 */
	public static class Char {
		public char value;
	}
}