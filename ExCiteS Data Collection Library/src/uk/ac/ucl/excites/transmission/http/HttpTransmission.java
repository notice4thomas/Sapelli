package uk.ac.ucl.excites.transmission.http;

import uk.ac.ucl.excites.storage.model.Record;
import uk.ac.ucl.excites.storage.model.Schema;
import uk.ac.ucl.excites.transmission.Transmission;
import uk.ac.ucl.excites.transmission.Settings;

public class HttpTransmission extends Transmission
{

	public HttpTransmission(Schema schema, Settings settings)
	{
		super(schema, settings);
	}

	@Override
	public boolean addRecord(Record record)
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void send()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void receive() throws Exception
	{
		// TODO Auto-generated method stub
		
	}

}