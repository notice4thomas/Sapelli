package uk.ac.ucl.excites.sapelli.collector.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import uk.ac.ucl.excites.sapelli.collector.model.fields.ChoiceField;
import uk.ac.ucl.excites.sapelli.shared.util.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.shared.util.io.FileWriter;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.util.IntegerRangeMapping;
import uk.ac.ucl.excites.sapelli.transmission.Settings;

/**
 * A Sapelli Collector Project (i.e. a survey consisting of one or more forms)
 * 
 * @author mstevens
 */
public class Project
{
	
	//STATICS-------------------------------------------------------------
	static public final int PROJECT_ID_SIZE = Schema.V1X_SCHEMA_ID_SIZE; // = 24 bits (kept the same was the v1.x Schema#id, for backwards compatibility)
	static public final IntegerRangeMapping PROJECT_ID_FIELD = IntegerRangeMapping.ForSize(0, PROJECT_ID_SIZE); // unsigned(!) 24bit integer (compatible with old schemaID)
	
	static public final String DEFAULT_VERSION = "0";
	
	static public final int PROJECT_HASH_SIZE = 32; //bits
	static public final IntegerRangeMapping PROJECT_HASH_FIELD = IntegerRangeMapping.ForSize(0, PROJECT_HASH_SIZE); // signed(!) 32bit integer (like Java hashCodes)
	
	static public final int MAX_FORMS = (int) Form.FORM_POSITION_FIELD.getHighBound() + 1; // = 15 + 1 = 16 forms allowed
	
	// Backwards compatibility:
	static public final int PROJECT_ID_V1X_TEMP = -1;
	
	// Subfolders:
	static public final String IMAGE_FOLDER = "img";
	static public final String SOUND_FOLDER = "snd";
	static public final String DATA_FOLDER = "data";
	static public final String TEMP_FOLDER = "temp";
	static public final String LOG_FOLDER = "logs"; //subfolder of data/
	static public final String DOCS_FOLDER = "docs";
	
	static public final String NO_MEDIA_FILE = ".nomedia"; //Info: http://www.makeuseof.com/tag/hide-private-picture-folders-gallery-android
	
	static public final boolean DEFAULT_LOGGING = false;
	
	//DYNAMICS------------------------------------------------------------
	private int id = Integer.MIN_VALUE; // don't init to 0 because that is an acceptable project id, nor -1 because that is used as temporary indication of a v1x project
	private String name;
	private String variant;
	private String version;
	
	private String projectPath;
	
	private Settings transmissionSettings;
	private boolean logging;
	private final Schema heartbeatSchema;
	private final List<Form> forms;
	private transient List<Schema> schemata;
	private Form startForm;
	
	// For backwards compatibility:
	private boolean v1xProject = false;
	private int schemaVersion = -1; // don't init to 0 because that is an acceptable schema version
	
	public Project(int id, String name, String basePath)
	{
		this(id, name, DEFAULT_VERSION, basePath, false);
	}
	
	public Project(int id, String name, String version, String basePath, boolean createSubfolder)
	{
		if(name == null || name.isEmpty() || basePath == null || basePath.isEmpty())
			throw new IllegalArgumentException("Both a name and a valid path are required");
		if(version == null || version.isEmpty())
			throw new IllegalArgumentException("A valid version is required");
		
		// Project id:
		if(id == PROJECT_ID_V1X_TEMP)
		{	//Backwards compatibility
			this.id = id;
			v1xProject = true;
		}
		else
			setID(id); // checks if it fits in field	
		
		this.name = FileHelpers.makeValidFileName(name);
		this.version = version;
		// Path:
		if(basePath.charAt(basePath.length() - 1) != File.separatorChar)
			basePath += File.separatorChar;
		this.projectPath = basePath;
		if(createSubfolder)
		{
			this.projectPath += this.name + File.separatorChar + "v" + version + File.separatorChar;
			if(!FileHelpers.createFolder(projectPath))
				throw new IllegalArgumentException("Could not create folder: " + projectPath);
			// Create .nomedia file:
			try
			{
				(new File(projectPath + NO_MEDIA_FILE)).createNewFile();
			}
			catch(IOException ignore) {}
		}
		// Heartbeat schema:
		this.heartbeatSchema = new HeartbeatSchema(this);
		// Forms list:
		this.forms = new ArrayList<Form>();
		// Logging:
		this.logging = DEFAULT_LOGGING;
	}
	
	/**
	 * Must be stay private!
	 * 
	 * @param id
	 */
	private void setID(int id)
	{
		if(PROJECT_ID_FIELD.fits(this.id)) // check is there is already a valid id set
			throw new IllegalStateException("Project id cannot be changed after it has been set.");
		if(PROJECT_ID_FIELD.fits(id))
			this.id = id;
		else
			throw new IllegalArgumentException("Invalid schema ID, valid values are " + PROJECT_ID_FIELD.getLogicalRangeString() + ".");
	}
	
	/**
	 * @return the id
	 */
	public int getID()
	{
		return id;
	}
	
	/**
	 * @return the v1xProject
	 */
	public boolean isV1xProject()
	{
		return v1xProject;
	}
	
	/**
	 * Method for backwards compatibility with v1.x projects. Passes id & version of the schema of the (assumed only) form to the project.
	 * 	- project id will be set to schema id of 1st (and assumed only) form
	 *  - schema version of that form will be stored in schemaVersion variable (to be able to parse old record export xml files)
	 * Can be used on v1.x projects only!
	 * 
	 * @param schemaID
	 * @param schemaVersion
	 */
	public void setV1XSchemaInfo(int schemaID, int schemaVersion)
	{
		if(!v1xProject)
			throw new IllegalStateException("Only allowed for v1.x projects (created with id=PROJECT_ID_V1X_TEMP).");
		setID(schemaID); // schemaID of first (and only) form is also used as projectID
		if(Schema.V1X_SCHEMA_VERSION_FIELD.fits(schemaVersion))
			this.schemaVersion = schemaVersion;
		else
			throw new IllegalArgumentException("Invalid schema version, valid values are " + Schema.V1X_SCHEMA_VERSION_FIELD.getLogicalRangeString() + ".");
	}
	
	/**
	 * @return the schemaVersion
	 */
	public int getSchemaVersion()
	{
		if(!v1xProject)
			throw new IllegalStateException("Only supported for v1.x projects.");
		return schemaVersion;
	}

	public String getName()
	{
		return name;
	}
	
	/**
	 * @return the variant
	 */
	public String getVariant()
	{
		return variant;
	}

	/**
	 * @param variant the variant to set
	 */
	public void setVariant(String variant)
	{
		if(this.variant != null)
			throw new IllegalStateException("Variant cannot be changed after it has been set.");
		if(variant != null && !variant.isEmpty())
			this.variant = variant;
	}

	public String getVersion()
	{
		return version;
	}
	
	/**
	 * Add a {@link Form} to the project
	 * 
	 * @param frm
	 */
	public void addForm(Form frm)
	{
		if(forms.size() >= MAX_FORMS)
			throw new IllegalStateException("Project cannot hold more than " + MAX_FORMS + " forms.");
		forms.add(frm);
		if(forms.size() == 1) //first form becomes startForm by default
			startForm = frm;
		// Throw away schemata list so it is recreated when requested:
		this.schemata = null;
	}
	
	public List<Form> getForms()
	{
		return forms;
	}
	
	public List<Schema> getSchemata()
	{
		if(schemata == null)
		{
			List<Schema> schemata = new ArrayList<Schema>();
			// Heartbeat always takes possition 0:
			schemata.add(heartbeatSchema);
			// Add data-producing forms:
			for(Form f : forms)
				/* TODO */;
		}
		return schemata;
	}
	
	/**
	 * @param position
	 * @return	the {@link Form} with the specified {@code position}, or {@code null} if the project has no such form. 
	 */
	public Form getForm(int position)
	{
		if(position >= 0 && position < forms.size())
			return forms.get(position);
		else
			return null; //no such form
	}
	
	/**
	 * @param id
	 * @return	the {@link Form} with the specified {@code id}, or {@code null} if the project has no such form.
	 */
	public Form getForm(String id)
	{
		for(Form f : forms)
			if(f.getID().equalsIgnoreCase(id)) // form IDs are treated as case insensitive
				return f;
		return null; //no such form
	}
	
	/**
	 * @return the startForm
	 */
	public Form getStartForm()
	{
		return startForm;
	}

	/**
	 * @param startForm the startForm to set
	 */
	public void setStartForm(Form startForm)
	{
		if(forms.contains(startForm))
			this.startForm = startForm;
		else
			throw new IllegalArgumentException("Unknown form.");
	}
	
	/**
	 * @param currentForm
	 * @return the form following the given one
	 */
	public Form getNextForm(Form currentForm)
	{
		if(currentForm.getPosition() + 1 < forms.size())
			return forms.get(currentForm.getPosition() + 1); // go to next field in the form
		else
			return null;
	}

	/**
	 * @return the transmissionSettings
	 */
	public Settings getTransmissionSettings()
	{
		return transmissionSettings;
	}

	/**
	 * @param transmissionSettings the transmissionSettings to set
	 */
	public void setTransmissionSettings(Settings transmissionSettings)
	{
		this.transmissionSettings = transmissionSettings;
	}
	
	public String getProjectFolderPath()
	{
		return projectPath;
	}
	
	public String getImageFolderPath()
	{
		return projectPath + IMAGE_FOLDER + File.separator;
	}
	
	public String getSoundFolderPath()
	{
		return projectPath + SOUND_FOLDER + File.separator;
	}
	
	public String getDataFolderPath()
	{
		return projectPath + DATA_FOLDER + File.separator;
	}
	
	/**
	 * @param imageFileRelativePath
	 * @return file object, or null if the given path was null or empty
	 */
	public File getImageFile(String imageFileRelativePath)
	{
		if(imageFileRelativePath == null || imageFileRelativePath.isEmpty())
			return null;
		return new File(getImageFolderPath() + imageFileRelativePath);
	}
	
	/**
	 * @param soundFileRelativePath
	 * @return file object, or null if the given path was null or empty
	 */
	public File getSoundFile(String soundFileRelativePath)
	{
		if(soundFileRelativePath == null || soundFileRelativePath.isEmpty())
			return null;
		return new File(getSoundFolderPath() + soundFileRelativePath);
	}
	
	/**
	 * @return File object pointing to the data folder for this project
	 * @throws IOException - when the folder cannot be created or is not writable
	 */
	public File getDataFolder() throws IOException
	{
		File folder = new File(getDataFolderPath());
		checkFolder(folder);
		return folder;
	}
	
	public String getTempFolderPath()
	{
		return projectPath + TEMP_FOLDER + File.separator;
	}
	
	/**
	 * @return File object pointing to the temp folder for this project
	 * @throws IOException - when the folder cannot be created or is not writable
	 */
	public File getTempFolder() throws IOException
	{
		File folder = new File(getTempFolderPath());
		checkFolder(folder);
		return folder;
	}
	
	public String toString()
	{
		return name + (variant != null ? (" " + variant) : "") + (version != DEFAULT_VERSION ? " (v" + version + ")" : "");
	}
	
	private void checkFolder(File folder) throws IOException
	{
		// Check if data path is accessible
		if(!FileHelpers.createFolder(folder))
			throw new IOException("Data path (" + folder.getAbsolutePath() + ") cannot be created.");
		if(!folder.canWrite())
			throw new IOException("Data path (" + folder.getAbsolutePath() + ") is not writable.");
	}

	/**
	 * @return the logging
	 */
	public boolean isLogging()
	{
		return logging;
	}

	/**
	 * @param logging the logging to set
	 */
	public void setLogging(boolean logging)
	{
		this.logging = logging;
	}
	
	public String getLogFolderPath() throws IOException
	{
		return getDataFolder().getAbsolutePath() + File.separator + LOG_FOLDER + File.separator;
	}
	
	public File getLogFolder() throws IOException
	{
		File folder = new File(getLogFolderPath());
		checkFolder(folder);
		return folder;
	}
	
	public String getDocsFolderPath()
	{
		return projectPath + DOCS_FOLDER + File.separator;
	}
	
	/**
	 * @return File object pointing to the docs folder for this project
	 * @throws IOException - when the folder cannot be created or is not writable
	 */
	public File getDocsFolder() throws IOException
	{
		File folder = new File(getDocsFolderPath());
		checkFolder(folder);
		return folder;
	}
	
	/**
	 * @return a list of files (as paths relative to the project path) that are referred to by (forms of) this project but which could not be found or accessed   
	 */
	public List<String> checkForInvalidFiles()
	{
		List<String> invalidFiles = new ArrayList<String>();
		for(Form form : forms)
			for(File file : form.getFiles(this))
				if(!file.isFile() || !file.exists() || !file.canRead())
					invalidFiles.add(file.getAbsolutePath().substring(projectPath.length()));
		return invalidFiles;
	}
	
	/**
	 * For now this only generates CSV files that document the indexed values for ChoiceFields
	 * 
	 * @throws IOException
	 */
	public void generateDocumentation() throws IOException
	{
		File docsFolder = getDocsFolder();
		for(Form form : forms)
		{
			for(Field field : form.getFields())
			{
				if(!field.isNoColumn() && field instanceof ChoiceField)
				{					
					FileWriter writer = new FileWriter(docsFolder.getAbsolutePath() + File.separator + form.getName() + "_" + field.getID() + ".csv");
					writer.open(FileHelpers.FILE_EXISTS_STRATEGY_REPLACE, FileHelpers.FILE_DOES_NOT_EXIST_STRATEGY_CREATE);
					writer.write(((ChoiceField) field).getDictionary().toCSV(";"));
					writer.close();
				}
			}
		}
	}
	
	public boolean equalSignature(Project other)
	{
		return 	this.name.equals(other.name)
				&& (this.variant == null ? other.variant == null : variant.equals(other.variant))
				&& this.version.equals(other.version);	
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true; // references to same object
		if(obj instanceof Project)
		{
			Project that = (Project) obj;
			if(this.id != that.id)
				return false;
			if(!equalSignature(that)) // checks name, variant & version
				return false;
			// TODO transmission settings?
			if(this.logging != that.logging)
				return false;
			if(!this.forms.equals(that.forms))
				return false;
			if(this.startForm != null ? !this.startForm.equals(that.startForm) : that.startForm != null)
				return false;
			if(this.v1xProject != that.v1xProject)
				return false;
			if(this.schemaVersion != that.schemaVersion)
				return false;
			return true;
		}
		else
			return false;
	}
	
	@Override
	public int hashCode()
	{
		int hash = 1;
		hash = 31 * hash + id;
		hash = 31 * hash + name.hashCode();
		hash = 31 * hash + (variant == null ? 0 : variant.hashCode());
		hash = 31 * hash + version.hashCode();
		// TODO include tranmssion settings?
		hash = 31 * hash + (logging ? 0 : 1);
		hash = 31 * hash + forms.hashCode();
		hash = 31 * hash + (startForm == null ? 0 : startForm.hashCode());
		hash = 31 * hash + (v1xProject ? 0 : 1);
		hash = 31 * hash + schemaVersion;
		return hash;
	}
	
}

