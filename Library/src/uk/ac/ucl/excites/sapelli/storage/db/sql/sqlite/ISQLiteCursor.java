/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2014 University College London - ExCiteS group
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

package uk.ac.ucl.excites.sapelli.storage.db.sql.sqlite;

/**
 * The methods of the interface deliberately matches the signatures of methods in {@link android.database.sqlite.SQLiteCursor}.
 * This is useful in AndroidSQLiteRecordStore, there we use a custom CursorFactor which creates instances of our own SQLiteCursor subclass
 * which implements ISQLiteCursor but doesn't have to implement the methods because they already exist in SQLiteCursor.
 * The purpose of all of this is to allow the (Android-agnostic) SQLiteColumn subclasses to call these methods on cursors.
 * 
 * @author mstevens
 *
 */
public interface ISQLiteCursor
{

	public byte[] getBlob(int columnIdx);
	
	public long getLong(int columnIdx);
	
	public double getDouble(int columnIdx);
	
	public String getString(int columnIdx);
	
	public boolean isNull(int columnIdx);

}