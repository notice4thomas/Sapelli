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

package uk.ac.ucl.excites.sapelli.transmission.model.content;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import uk.ac.ucl.excites.sapelli.shared.compression.CompressorFactory.Compression;
import uk.ac.ucl.excites.sapelli.shared.io.BitArray;
import uk.ac.ucl.excites.sapelli.shared.io.BitArrayInputStream;
import uk.ac.ucl.excites.sapelli.shared.io.BitArrayOutputStream;
import uk.ac.ucl.excites.sapelli.shared.io.BitInputStream;
import uk.ac.ucl.excites.sapelli.shared.io.BitOutputStream;
import uk.ac.ucl.excites.sapelli.shared.io.StreamHelpers;
import uk.ac.ucl.excites.sapelli.shared.util.IntegerRangeMapping;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.storage.model.Model;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.util.UnknownModelException;
import uk.ac.ucl.excites.sapelli.transmission.TransmissionClient;
import uk.ac.ucl.excites.sapelli.transmission.model.Payload;
import uk.ac.ucl.excites.sapelli.transmission.util.TransmissionCapacityExceededException;
import uk.ac.ucl.excites.sapelli.transmission.util.TransmissionSendingException;

/**
 * @author mstevens
 */
public class RecordsPayload extends Payload
{

	// STATIC----------------------------------------------
	/**
	 * Records payload format V2, which was introduced in Sapelli v2.0.
	 * This not in compatible with the format used in v1.x, which is no longer supported in Sapelli v2.0.
	 * V2 is currently the only support format but in the future variations/extension could be introduced.
	 */
	static protected final short V2_FORMAT = 2;
	
	/**
	 * The default Records payload format version being used.
	 */
	static protected final short DEFAULT_FORMAT = V2_FORMAT;
	
	/**
	 * The highest supported Records payload format version
	 */
	static protected final short HIGHEST_SUPPORTED_FORMAT = V2_FORMAT;
	
	/**
	 * We use 2 bits to store the format version This means up to 4 versions can be differentiated.
	 * Currently only 1 supported format exists (= V2). If we ever get to V5 it would be best if an
	 * additional flag is added to enable future extensions beyond V5.
	 */
	static protected final short FORMAT_VERSION_SIZE = 2; // bits
	
	/**
	 * The field used to indicate the version of the Records payload format which is being used.
	 */
	static protected final IntegerRangeMapping FORMAT_VERSION_FIELD = IntegerRangeMapping.ForSize(V2_FORMAT, FORMAT_VERSION_SIZE); // can take values from [2, 5] (but stored binary as [0, 3])
	
	static protected final Compression[] COMPRESSION_MODES = { Compression.NONE, Compression.DEFLATE, Compression.LZMA };
	static protected final IntegerRangeMapping COMPRESSION_FLAG_FIELD = new IntegerRangeMapping(0, COMPRESSION_MODES.length - 1);
	
	static public int GetType()
	{
		return BuiltinType.Records.ordinal();
	}
	
	/**
	 * By default RecordsPayload uses lossy encoding on columns which support it.
	 */
	static private final boolean DEFAULT_LOSSLESS_ENCODING = false; 
	
	// DYNAMIC---------------------------------------------
	protected Model model;
	protected final Map<Schema, List<Record>> recordsBySchema;
	
	/**
	 * Whether or not to force lossless encoding across all columns.
	 */
	protected boolean lossless;
	
	public RecordsPayload()
	{
		this(DEFAULT_LOSSLESS_ENCODING);
	}
	
	/**
	 * To be used on the sending side only.
	 * 
	 * @param lossless whether or not to force lossless encoding across all columns.
	 */
	public RecordsPayload(boolean lossless)
	{
		this.lossless = lossless;
		this.recordsBySchema = new HashMap<Schema, List<Record>>();
	}
	
	@Override
	public int getType()
	{
		return GetType();
	}
	
	/**
	 * To be called from the sending side
	 * 
	 * TODO OPTIMISE THIS
	 * 
	 * @param record the record to add
	 * @throws IllegalStateException when no transmission is set
	 * @throws IllegalArgumentException when schema of given record is not transmittable or the record is not filled (some non-optional, transmittable values are null)
	 * @throws TransmissionCapacityExceededException when adding this record caused transmission capacity to be exceeded
	 * @throws TransmissionSendingException when another Transmission preparation or I/O problem occurs
	 */
	public void addRecord(Record record) throws IllegalStateException, IllegalArgumentException, TransmissionCapacityExceededException, TransmissionSendingException
	{
		if(!isTansmissionSet())
			throw new IllegalStateException("No transmission set!");
		
		Schema schema = record.getSchema();

		// Check if transmittable:
		if(!schema.hasFlags(TransmissionClient.SCHEMA_FLAG_TRANSMITTABLE))
			throw new IllegalArgumentException("Schema of given record(s) is not transmittable.");
		
		// Check if record has non-null values for all transmittable columns:
		if(!record.isFilled(transmission.client.getNonTransmittableColumns(schema))) // includes auto-incr-PK columns
			throw new IllegalArgumentException("Record is not filled (some non-optional, transmittable values are null).");
		
		// Model:
		if(recordsBySchema.isEmpty())
			// set model:
			model = schema.getModel();
		//	Check model:
		else if(model != schema.getModel())
			throw new IllegalArgumentException("The schemata of the records in a single Transmission must all belong to the same model.");
		
		// Add the record to the list of attached records for this schema:
		List<Record> recordsOfSchema = recordsBySchema.get(schema);
		if(recordsOfSchema == null)
		{
			recordsOfSchema = new ArrayList<Record>();
			recordsBySchema.put(schema, recordsOfSchema);
		}
		recordsOfSchema.add(record);
		
		// Try serialising and check capacity:
		try
		{
			transmission.checkCapacity();
		}
		catch(TransmissionSendingException e)
		{	// Adding this record caused transmission capacity to be exceeded, or an IO problem occurred:
			// 	Undo adding of record:
			recordsOfSchema.remove(record);
			if(recordsOfSchema.isEmpty())
				recordsBySchema.remove(schema);
			//	Re-throw exception:
			throw e;
		}
	}
	
	/**
	 * @return records grouped by schema
	 */
	public Map<Schema,List<Record>> getRecordsBySchema()
	{
		return recordsBySchema;
	}
	
	/**
	 * @return flat list of records (sorted by Schema)
	 */
	public List<Record> getRecords()
	{
		List<Record> allRecords = new ArrayList<Record>();
		for(Entry<Schema, List<Record>> entry : recordsBySchema.entrySet())
			allRecords.addAll(entry.getValue());
		return allRecords;
	}
	
	public Model getModel()
	{
		return model;
	}
	
	/**
	 * @return all schemata for which the transmission contains records 
	 */
	public Set<Schema> getSchemata()
	{
		return recordsBySchema.keySet();
	}
	
	public boolean containsRecordsOf(Schema schema)
	{
		List<Record> recList = recordsBySchema.get(schema);
		return recList != null && !recList.isEmpty();
	}
	
	/**
	 * @return whether the transmission contains records of more than 1 schema
	 */
	public boolean isMultiSchema()
	{
		return recordsBySchema.keySet().size() > 1;
	}
	
	public int getNumberOfRecords()
	{
		int total = 0;
		for(List<Record> recordsOfSchema : recordsBySchema.values())
			total += recordsOfSchema.size();
		return total;
	}
	
	public boolean isEmpty()
	{
		return recordsBySchema.isEmpty();
	}
	
	/**
	 * @return whether or not this RecordsPayload encodes records losslessly
	 */
	public boolean isUsingLosslessEncoding()
	{
		return lossless;
	}
	
	/**
	 * @return whether or not this RecordsPayload encodes records lossyly
	 */
	public boolean isUsingLossyEncoding()
	{
		return !lossless;
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.transmission.Payload#doSerialise(uk.ac.ucl.excites.sapelli.shared.io.BitOutputStream)
	 */
	@Override
	protected void write(BitOutputStream out) throws IllegalStateException, IOException, TransmissionCapacityExceededException
	{
		if(recordsBySchema.isEmpty())
			throw new IllegalStateException("Payload contains no records. Add at least 1 record before serialising.");
		try
		{
			int numberOfDifferentSchemataInTransmission = getSchemata().size();
			Schema[] schemataInT = new Schema[numberOfDifferentSchemataInTransmission];
			
			// Write HEADER PART 1 ----------------------------------
			//	Format version (2 bits):
			FORMAT_VERSION_FIELD.write(DEFAULT_FORMAT, out);
			//	Lossless flag:
			out.write(lossless);
			//	Model & schema identification:
			// 		Write Model ID (56 bits):
			Model.MODEL_ID_FIELD.write(model.getID(), out);
			//		Write schema occurrence bits:
			int s = 0;
			for(Schema sInM : model.getSchemata())
			{	// 1 bit per schema in model, indicating for which schemata this payload contains records (schemata in model order):
				if(containsRecordsOf(sInM))
				{
					out.write(true); // transmission payload contains at least 1 record for this schema
					schemataInT[s++] = sInM;
				}
				else
					out.write(false); // no records of this schema appear in this transmission payload
			}

			// Encode records ---------------------------------------
			BitArray recordsBits = encodeRecords(schemataInT);
			// Compress record bits with various compression modes:
			byte[][] comprResults = Compress(recordsBits, COMPRESSION_MODES);
			// Determine most space-efficient compression mode:
			int bestComprIdx = 0;
			for(int c = 1; c < COMPRESSION_MODES.length; c++)
				if(comprResults[c].length < comprResults[bestComprIdx].length)
					bestComprIdx = c;
			
			// Write HEADER PART 2 ----------------------------------
			//	Compression flag (2 bits):
			COMPRESSION_FLAG_FIELD.write(bestComprIdx, out);

			// Write BODY: the encoded & compressed records ---------
			if(COMPRESSION_MODES[bestComprIdx] != Compression.NONE) // if compressed : write byte array 
				out.write(comprResults[bestComprIdx]); // write byte array
			else
				recordsBits.writeTo(out); // write bit array (avoid padding to byte boundary)
		}
		catch(IOException e)
		{
			throw new IOException("Error on serialising payload.", e);
		}
	}
	
	/**
	 * Note: SMSTransmission overrides this to insert a completeness check
	 * 
	 * @param BitInputStream
	 * @throws IOException
	 * @throws RecordsPayloadDecodeException
	 * 
	 * @see uk.ac.ucl.excites.sapelli.transmission.model.Payload#read(uk.ac.ucl.excites.sapelli.shared.io.BitInputStream)
	 */
	@Override
	protected void read(BitInputStream in) throws IOException, RecordsPayloadDecodeException
	{
		// Read HEADER ----------------------------------------------
		//	Format version:
		short format = FORMAT_VERSION_FIELD.readShort(in);
		if(format > HIGHEST_SUPPORTED_FORMAT)
			throw new RecordsPayloadDecodeException(this, "Unsupported payload format version: " + format + " (highest supported version: " + HIGHEST_SUPPORTED_FORMAT + ").");
		//	Lossless flag:
		this.lossless = in.readBit();
		//	Model & schema identification:
		//		Read Model ID & loop-up model:
		try
		{
			this.model = transmission.client.getModel(Model.MODEL_ID_FIELD.readLong(in));
		}
		catch(UnknownModelException ume)
		{
			throw new RecordsPayloadDecodeException(this, ume);
		}
		//		Read schema occurrence bits:
		List<Schema> schemataInT = new ArrayList<Schema>();
		for(Schema sInM : model.getSchemata())
			if(in.readBit())
				schemataInT.add(sInM);
		//	Compression flag:
		int compressionMode = COMPRESSION_FLAG_FIELD.readInt(in);

		// Read BODY: encoded records, possibly compressed ----------
		BitArray recordsBits;
		if(COMPRESSION_MODES[compressionMode] == Compression.NONE)
			recordsBits = in.readBitArray(in.bitsAvailable()); // not compressed: read as bits
		else
		{	// Read compressed data as bytes & decompress them:
			byte[] recordBytes = Decompress(in.readBytes(in.available()), COMPRESSION_MODES[compressionMode]);
			// Convert to bit array:
			recordsBits = BitArray.FromBytes(recordBytes);
		}
		
		// Decode records:
		decodeRecords(schemataInT, recordsBits);
	}
	
	/**
	 * @param schemataInT
	 * @return
	 * @throws IOException
	 * @throws TransmissionCapacityExceededException
	 */
	protected BitArray encodeRecords(Schema[] schemataInT) throws IOException, TransmissionCapacityExceededException
	{
		BitArrayOutputStream out = null;
		try
		{
			out = new BitArrayOutputStream();
			IntegerRangeMapping numberOfRecordsPerSchemaField = getNumberOfRecordsPerSchemaField(schemataInT.length);
			
			// Encode records per schema...
			for(Schema schema : schemataInT)
			{
				// Get columns which should *not* be transmitted:
				Set<Column<?>> nonTransmittableColumns = transmission.client.getNonTransmittableColumns(schema); // includes auto-incr-PK columns
				
				// Get records:
				List<Record> records = recordsBySchema.get(schema);
				
				// Write number of records:
				if(numberOfRecordsPerSchemaField.inEffectiveRange(records.size()))
					numberOfRecordsPerSchemaField.write(records.size(), out); // write number of records that will follow
				else
					throw new TransmissionCapacityExceededException("Cannot fit " + records.size() + " of schema " + schema.getName() + " (max allowed: " + numberOfRecordsPerSchemaField.highBound(false) + ").");
				
				// Factoring-out logic ...
				Map<Column<?>, BitArray> factoredOutValues = Collections.<Column<?>, BitArray> emptyMap();
				if(records.size() > 1)
				{	// Only if there is more than 1 record for this schema:
					// 	Get factored out values ...
					factoredOutValues = new HashMap<Column<?>, BitArray>();
					boolean first = true;
					for(Record r : records)
					{
						if(first)
						{	// Get values of first record:
							for(Column<?> c : schema.getColumns(false))
								if(!nonTransmittableColumns.contains(c)) // ignore non-transmittable columns
									factoredOutValues.put(c, c.retrieveValueAsBits(records.get(0), lossless)); // treat all columns as potentially factored-out
							first = false;
						}
						else
						{	// Check if these values are these same in subsequent records:
							for(Iterator<Map.Entry<Column<?>, BitArray>> it = factoredOutValues.entrySet().iterator(); it.hasNext();) // use an iterator so we can remove in the for-loop
							{
								Map.Entry<Column<?>, BitArray> entry = it.next();
								if(!entry.getValue().equals(entry.getKey().retrieveValueAsBits(r, lossless)))
									it.remove(); // value mismatch -> this column can not be factored out
							}
							if(factoredOutValues.isEmpty())
								break; // no factored-out columns left -> no need to loop over rest of the records
						}
					}
					//	Write factoring-out header (including factored-out values, if used):
					if(!factoredOutValues.isEmpty())
					{
						// Write flag which indicates that factoring-out is used:
						out.write(true);
						// Write factored-out flags & the actual factored out values:
						for(Column<?> c : schema.getColumns(false))
						{	// for all transmittable columns:
							if(!nonTransmittableColumns.contains(c))
							{
								if(factoredOutValues.containsKey(c))
								{	// Column is factored out:
									out.write(true); // write factored-out flag = true
									out.write(factoredOutValues.get(c)); // write factored out value
								}
								else
									// Column is *not* factored out:
									out.write(false); // write factored-out flag = false
							}
						}
					}
					else
						// Write flag which indicates that factoring-out is *not* used:
						out.write(false);
				}
				
				// Write record data, skipping ...
				Set<Column<?>> skipColumns = new HashSet<Column<?>>(nonTransmittableColumns); 	// ... non-transmittable,
				skipColumns.addAll(factoredOutValues.keySet());									// factored-out, ...
				for(Record r : recordsBySchema.get(schema))
					r.writeToBitStream(out, false /* ... and virtual columns */, skipColumns, lossless);
			}
			
			// Close the stream & return bits:
			out.close();
			return out.toBitArray();
		}
		catch(Exception e)
		{
			throw new IOException("Error on encoding records.", e);
		}
		finally
		{
			StreamHelpers.SilentClose(out);
		}
	}
	
	/**
	 * @param schemataInT
	 * @param recordsBits
	 * @throws RecordsPayloadDecodeException
	 */
	protected void decodeRecords(List<Schema> schemataInT, BitArray recordsBits) throws RecordsPayloadDecodeException
	{		
		BitInputStream in = null;
		Record record = null;
		try
		{
			in = new BitArrayInputStream(recordsBits);
			IntegerRangeMapping numberOfRecordsPerSchemaField = getNumberOfRecordsPerSchemaField(schemataInT.size());
			// Per schema...
			for(Schema schema : schemataInT)
			{
				// Get columns which should *not* be transmitted:
				Set<Column<?>> nonTransmittableColumns = transmission.client.getNonTransmittableColumns(schema); // includes auto-incr-PK columns
				
				// Create & store list for the records that will be decoded:
				List<Record> records = new ArrayList<Record>();
				recordsBySchema.put(schema, records);
				
				// Read number of records:
				int numberOfRecordsForSchema = numberOfRecordsPerSchemaField.readInt(in);
				
				// Factoring-out logic ...
				Map<Column<?>, Object> factoredOutValues = Collections.<Column<?>, Object> emptyMap();
				if(numberOfRecordsForSchema > 1)
				{	// Only if there is more than 1 record for this schema:
					// 	Read factoring-out header (including factored-out values, if used) ...
					if(in.readBit()) //	read flag that indicates whether or not some columns are factored-out
					{
						factoredOutValues = new HashMap<Column<?>, Object>();
						for(Column<?> c : schema.getColumns(false))
						{	// for all transmittable columns:
							if(!nonTransmittableColumns.contains(c))
							{
								if(in.readBit()) // Read factored-out flag, indicating whether column is factored-out; if = true: 
									factoredOutValues.put(c, c.readValue(in, lossless)); // read factored out value
							}
						}
					}
				}
				
				// Read record data, skipping ...
				Set<Column<?>> skipColumns = new HashSet<Column<?>>(nonTransmittableColumns); 	// ... non-transmittable,
				skipColumns.addAll(factoredOutValues.keySet());									// factored-out, ...
				while(	records.size() < numberOfRecordsForSchema &&					
						in.bitsAvailable() >= schema.getMinimumSize(false /* ... and virtual columns */, skipColumns, lossless))
				{
					// Get new Record instance:
					record = schema.createRecord();
					// Read record values from the stream, skipping virtual columns and factored-out columns:
					record.readFromBitStream(in, false /* ... and virtual columns */, skipColumns, lossless);
					// Set factored-out values:
					for(Entry<Column<?>, Object> fEntry : factoredOutValues.entrySet())
						fEntry.getKey().storeObject(record, fEntry.getValue());
					// Add the record:
					records.add(record);
				}
			}
		}
		catch(Exception e)
		{
			RecordsPayloadDecodeException de = new RecordsPayloadDecodeException(this, "Error on decoding records.", e); //pass schema used for decoding and records decoded so far 
			de.addPartialRecord(record); //add last (partially decoded) record (will be ignored if null)
			recordsBySchema.clear(); //remove partially decoded records
			throw de;
		}
		finally
		{
			StreamHelpers.SilentClose(in);
		}
	}
	
	/**
	 * The number of bits available to encode all records (*including* the space used by the "numberOfRecordPerSchemaFields"),
	 * under the assumption no compression will be used (i.e. "without compression" should *not* be interpreted as "before compression").
	 * 
	 * @return number of bits
	 */
	private int getMaxUncompressedRecordsBits()
	{
		return	transmission.getMaxPayloadBits()
				- FORMAT_VERSION_SIZE				// Format version
				- 1									// Lossless flag
				- Model.MODEL_ID_SIZE				// Model ID
				- model.getNumberOfSchemata()		// Schema occurrence bits
				- COMPRESSION_FLAG_FIELD.size();	// Compression flag
	}
	
	/**
	 * Returns the field which we will use to write the number of records of a given schema are
	 * contained in the payload. We do not attempt to compute the actual maximum number of records
	 * that can be fitted because doing so is inherently inaccurate because the use of compression
	 * could increase this number in unpredictable ways. Instead the maximum number of records per
	 * schema is taken as the maximum payload size of the transmission (decreased by the space
	 * taken up by the header(s)), divided by the number of different schemata in the payload.
	 * The field is generously sized (effectively allowing for a compressed size of about 1 bit per
	 * record) because we want to avoid limiting the number of records we can fit before compression
	 * is applied.
	 * 
	 * @param numberOfSchemata
	 * @return the field
	 */
	private IntegerRangeMapping getNumberOfRecordsPerSchemaField(int numberOfSchemata)
	{
		return new IntegerRangeMapping(1, (int) Math.ceil(getMaxUncompressedRecordsBits() / (double) numberOfSchemata));
	}

	@Override
	public boolean acknowledgeReception()
	{
		return true; // !!!
	}
	
	@Override
	public void handle(Handler handler) throws Exception
	{
		handler.handle(this);
	}

}
