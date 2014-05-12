/**
 * 
 */
package uk.ac.ucl.excites.sapelli.storage.db.db4o;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import uk.ac.ucl.excites.sapelli.shared.db.db4o.DB4OConnector;
import uk.ac.ucl.excites.sapelli.shared.util.TimeUtils;
import uk.ac.ucl.excites.sapelli.storage.StorageClient;
import uk.ac.ucl.excites.sapelli.storage.db.RecordStore;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.queries.RecordsQuery;
import uk.ac.ucl.excites.sapelli.storage.queries.SingleRecordQuery;
import uk.ac.ucl.excites.sapelli.storage.queries.constraints.Constraint;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Predicate;

/**
 * DB4O implementation of {@link RecordStore}.
 * 
 * @author mstevens
 */
public class DB4ORecordStore extends RecordStore
{

	// Statics----------------------------------------------
	static public final String DATABASE_NAME_SUFFIX = "_Data";
	static public final String BACKUP_SUFFIX = "_Backup";
	static public final int ACTIVATION_DEPTH = 40;
	static public final int UPDATE_DEPTH = 40;

	// Dynamics---------------------------------------------
	private ObjectContainer db4o;
	private String filename;
	
	public DB4ORecordStore(StorageClient client, File folder, String baseFilename) throws Exception
	{
		super(client);
		this.filename = baseFilename + DATABASE_NAME_SUFFIX;
		this.db4o = DB4OConnector.open(DB4OConnector.getFile(folder, filename), Record.class, Schema.class);
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#storeNonInternal(uk.ac.ucl.excites.sapelli.storage.model.Record)
	 */
	@Override
	public void storeNonInternal(Record record)
	{
		db4o.store(record);
		db4o.commit();
	}
	
	@Override
	public void store(List<Record> records)
	{
		for(Record r : records)
			db4o.store(r);
		db4o.commit();
	}
	
	@Override
	public void delete(List<Record> records)
	{
		for(Record r : records)
			db4o.delete(r);
		db4o.commit();
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordDataAccess#delete(uk.ac.ucl.excites.sapelli.storage.model.Record)
	 */
	@Override
	public void delete(Record record)
	{
		db4o.delete(record);
		db4o.commit();
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordDataAccess#retrieveRecords()
	 */
	@Override
	public List<Record> retrieveAllRecords()
	{
		ObjectSet<Record> resultSet = db4o.query(Record.class);
		
		// Activate result records & add to new ArrayList (list returned by DB4O doesn't allow sorting and possibly other things):
		List<Record> result = new ArrayList<Record>();
		for(Record r : resultSet)
		{
			db4o.activate(r, ACTIVATION_DEPTH);
			// Filter out records of internal schemas:
			if(!r.getSchema().isInternal())
				result.add(r);
		}
		return result;
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordDataAccess#deleteAllRecords()
	 */
	@Override
	public void deleteAllRecords()
	{
		List<Record> result = db4o.query(Record.class);
		for(Record r : result)
			db4o.delete(r);
		db4o.commit();
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordDataAccess#retrieveRecords(uk.ac.ucl.excites.sapelli.storage.queries.Query)
	 */
	@Override
	public List<Record> retrieveRecords(final RecordsQuery query)
	{		
		// Query for records:
		ObjectSet<Record> resultSet = db4o.query(new Predicate<Record>()
		{
			private static final long serialVersionUID = 1L;

			public boolean match(Record record)
			{
				return	!record.getSchema().isInternal()														/* filter out records of internal schemas */
						&& (query.isAnySchema() || query.getSourceSchemata().contains(record.getSchema()));	/* Schema check */
			}
		});
		
		// Activate result records, filter by query constraints & add to new ArrayList (list returned by DB4O doesn't allow sorting and possibly other things):
		List<Record> result = new ArrayList<Record>();
		Constraint constraints = query.getConstraints();
		while(resultSet.hasNext())
		{
			Record r = resultSet.next();
			db4o.activate(r, ACTIVATION_DEPTH);
			if(constraints.isValid(r)) // Filter by constraint(s) (for some reason the DB4O query doesn't work if this happens inside the Predicate's match() method)
				result.add(r);
		}
		
		// Sort result:
		query.sort(result);
		
		// Apply limit if necessary & return result:
		int limit = query.getLimit();
		if(limit != RecordsQuery.NO_LIMIT && result.size() > limit)
			return result.subList(0, limit);
		else
			return result;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordAccess#retrieveRecord(uk.ac.ucl.excites.sapelli.storage.queries.SingleRecordQuery)
	 */
	@Override
	public Record retrieveRecord(SingleRecordQuery query)
	{
		// Run the RecordsQuery:
		List<Record> records = retrieveRecords(query.getRecordsQuery());
		
		// Run execute the SingleRecordQuery (reducing the list to 1 record), without re-running the recordsQuery, and then return the result:
		return query.execute(records, false);
	}

	@Override
	public void finalise()
	{
		db4o.close();
	}

	@Override
	public void backup(File destinationFolder) throws Exception
	{
		db4o.commit();
		db4o.ext().backup(DB4OConnector.getFile(destinationFolder, filename + BACKUP_SUFFIX + "_" + TimeUtils.getTimestampForFileName()).getAbsolutePath());
	}

}
