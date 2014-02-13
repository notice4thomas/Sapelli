/**
 * 
 */
package uk.ac.ucl.excites.sapelli.storage.xml;

import java.util.List;

import uk.ac.ucl.excites.sapelli.collector.database.DataAccess;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.util.TimeUtils;
import uk.ac.ucl.excites.sapelli.util.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.util.io.FileWriter;
import uk.ac.ucl.excites.sapelli.util.xml.XMLUtils;


/**
 * @author mstevens
 *
 */
public class RecordsExporter
{

	static public final String TAG_RECORDS_EXPORT = "RecordsExport";
	
	private String exportFolderPath;
	private DataAccess dao;
	
	public RecordsExporter(String exportFolderPath, DataAccess dao)
	{
		if(!FileHelpers.createFolder(exportFolderPath))
			throw new IllegalArgumentException("Export folder (" + exportFolderPath + ") does not exist and could not be created!");
		this.exportFolderPath = exportFolderPath;
		this.dao = dao;
	}
	
	private FileWriter openWriter(String description) throws Exception
	{
		FileWriter writer = new FileWriter(exportFolderPath + "RecordDump_" + description + "_" + TimeUtils.getTimestampForFileName() + ".xml", "UTF-8");
		writer.open(FileHelpers.FILE_EXISTS_STRATEGY_REPLACE, FileHelpers.FILE_DOES_NOT_EXIST_STRATEGY_CREATE);
		writer.writeLine(XMLUtils.header());
		writer.writeLine("<" + TAG_RECORDS_EXPORT + ">");
		//TODO add attributes: exportDateTime, comment, device(?)
		return writer;
	}
	
	private void closeWriter(FileWriter writer)
	{
		writer.writeLine("</" + TAG_RECORDS_EXPORT + ">");
		writer.close();
	}
	
	public int exportAll() throws Exception
	{
		FileWriter writer = openWriter("ALL");
		int count = exportRecords(dao.retrieveRecords(), writer);
		closeWriter(writer);
		return count;
	}
	
	public int exportRecords(List<Record> records) throws Exception
	{
		return exportRecords(records, "Selection");
	}
	
	public int exportRecords(List<Record> records, String name) throws Exception
	{
		FileWriter writer = openWriter(name);
		int count = exportRecords(records, writer);
		closeWriter(writer);
		return count;
	}
	
	public int exportRecordsOf(List<Schema> schemas, String name) throws Exception
	{
		FileWriter writer = openWriter(name);
		int count = 0;
		for(Schema s : schemas)
			count += exportRecords(dao.retrieveRecords(s), writer);
		closeWriter(writer);
		return count;
	}
	
	public int exportRecordsOf(Schema s) throws Exception
	{
		FileWriter writer = openWriter(s.getName());
		int count = exportRecords(dao.retrieveRecords(s), writer);
		closeWriter(writer);
		return count;
	}
	
	private int exportRecords(List<Record> records, FileWriter writer)
	{
		int count = 0;
		for(Record r : records)
		{
			try
			{
				writer.writeLine(r.toXML(1));
			}
			catch(Exception e)
			{
				e.printStackTrace(System.err);
				writer.writeLine(XMLUtils.comment("Exception on exporting record: " + e.toString() + (e.getMessage() != null ? " [" + e.getMessage() + "]" : ""), 1));
			}
			count++;
		}
		return count;
	}
	
}