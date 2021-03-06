/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2016 University College London - ExCiteS group
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package uk.ac.ucl.excites.sapelli.shared.io;

import java.io.IOException;
import java.util.BitSet;
import java.util.Iterator;

import uk.ac.ucl.excites.sapelli.shared.util.BinaryHelpers;

/**
 * @author mstevens
 * 
 */
public class BitArray implements Iterable<Boolean>
{

	// STATIC -------------------------------------------------------
	/**
	 * Returns a new {@link BitArray} initialised using the given {@code bytes} and with length = {@code bytes.length} * 8.
	 * 
	 * @param bytes
	 * @return
	 */
	static public BitArray FromBytes(byte[] bytes)
	{
		return FromBytes(bytes, BitArrayOutputStream.UNLIMITED);
	}
	
	/**
	 * Returns a new {@link BitArray} with length = {@code bitLength} and initialised using the given {@code bytes} (up to the ({@code bitLength} - 1)th bit)
	 * 
	 * @param bytes
	 * @param bitLength
	 * @return
	 */
	static public BitArray FromBytes(byte[] bytes, int bitLength)
	{
		BitArrayOutputStream baos = new BitArrayOutputStream(bitLength);
		try
		{
			baos.write(bytes);
		}
		catch(CapacityReachedException full)
		{
			// do nothing
		}
		catch(IOException ioE)
		{
			return null; // this should never happen
		}
		finally
		{
			StreamHelpers.SilentClose(baos);
		}
		return baos.toBitArray(true); // use given max bitLength
	}
	
	// DYNAMIC ------------------------------------------------------
	private final BitSet bits;
	private final int length;
	
	/**
	 * @param length
	 */
	public BitArray(int length)
	{
		this(new BitSet(length), length);
	}
	
	/**
	 * @param bits
	 * @param length
	 */
	public BitArray(BitSet bits, int length)
	{
		if(bits == null)
			throw new NullPointerException("bits cannot be null!");
		if(length < 0)
			throw new IllegalArgumentException("length cannot be negative!");
		this.bits = bits;
		this.length = length;
	}

	public boolean get(int index)
	{
		if(index >= 0 && index < length)
			return bits.get(index);
		else
			throw new IndexOutOfBoundsException("index (" + index + ") out of bounds [0, " + (length - 1) + "]!");
	}
	
	public void set(int index, boolean value)
	{
		if(index >= 0 && index < length)
			bits.set(index, value);
		else
			throw new IndexOutOfBoundsException("index (" + index + ") out of bounds [0, " + (length - 1) + "]!");
	}
	
	@Override
	public Iterator<Boolean> iterator()
	{
		return new Iterator<Boolean>()
		{
			
			private int index = 0;

			@Override
			public boolean hasNext()
			{
				return index < length;
			}

			@Override
			public Boolean next()
			{
				return get(index++);
			}

			@Override
			public void remove()
			{
				throw new UnsupportedOperationException("Cannot remove items from BitArray");
			}
		};
	}

	/**
	 * @return the number of bits in the array
	 */
	public int length()
	{
		return length;
	}

	public byte[] toByteArray()
	{
		byte[] bytes = new byte[BinaryHelpers.bytesNeeded(length)];
		for(int i = 0; i < length; i++)
			if(bits.get(i))
				bytes[i / Byte.SIZE] |= 1 << (7 - (i % 8)); // MSB is read first
		return bytes;
	}
	
	/**
	 * Writes the bits to a {@link BitOutputStream}
	 * 
	 * @param bos the BitOutputStream
	 * @throws IOException if an I/O error occurs
	 */
	public void writeTo(BitOutputStream bos) throws IOException
	{
		for(int i = 0; i < length; i++)
			bos.write(bits.get(i));
	}
	
	public int hashCode()
	{
		int hash = 1;
		hash = 31 * hash + length;
		hash = 31 * hash + bits.hashCode();
		/*// Less efficient alternative for line above:
		for(int i = 0; i < length; i++)
			hash = 31 * hash + (get(i) ? 0 : 1);*/
		return hash;
	}
	
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(obj instanceof BitArray)
		{
			BitArray that = (BitArray) obj;
			if(this.length != that.length)
				return false;
			return this.bits.equals(that.bits);
			/*// Less efficient alternative for line above:
			for(int i = 0; i < length; i++)
				if(this.get(i) != that.get(i))
					return false;
			return true;*/
		}
		return false;
	}
	
	/**
	 * @param offset
	 * @param length
	 * @return a subArray of this one starting with the bit at position offset and the given length or less
	 */
	public BitArray subArray(int offset, int length)
	{
		if(offset < 0 || offset > this.length)
			throw new IndexOutOfBoundsException("offset (" + offset + ") out of bounds [0, " + (this.length - 1) + "]!");
		int to = offset + length;
		if(to > this.length)
			to = this.length;
		BitArray sub = new BitArray(to - offset);
		for(int i = offset; i < to; i++)
			sub.set(i, this.get(i));
		return sub;
	}
	
}
