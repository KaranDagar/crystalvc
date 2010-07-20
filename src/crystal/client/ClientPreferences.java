package crystal.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import crystal.Constants;
import crystal.model.DataSource;
import crystal.model.DataSource.RepoKind;
import crystal.util.XMLTools;

/**
 * Maintains multiple sets of preferences, rather than just one.
 * 
 * @author rtholmes & brun
 * 
 */
public class ClientPreferences {
	private interface IPrefXML {

		static final String ROOT = "ccConfig";

		static final String TMP_DIR = "tempDirectory";
		static final String HG_PATH = "hgPath";

		static final String PROJECT = "project";

		static final String SOURCE = "source";

		static final String KIND = "myKind";
		static final String CLONE = "myClone";
		static final String LABEL = "myShortName";
	}

	/**
	 * Path to the configuration (user.home)
	 */
	public static String CONFIG_PATH;

	public static Logger _log = Logger.getLogger(ClientPreferences.class);
	
	public static ClientPreferences DEFAULT_CLIENT_PREFERENCES;

	static {
		String path = System.getProperty("user.home");
		if (!path.endsWith(File.separator))
			path += File.separator;

		CONFIG_PATH = path + ".conflictClient.xml";
		
		DEFAULT_CLIENT_PREFERENCES = new ClientPreferences("/tmp/conflictClient/", "/path/to/hg");
		ProjectPreferences pp = new ProjectPreferences(new DataSource("myProject", "$HOME/dev/myProject/", DataSource.RepoKind.HG), DEFAULT_CLIENT_PREFERENCES);
		pp.addDataSource(new DataSource("jim", "https://path/to/repo", DataSource.RepoKind.HG));
		pp.addDataSource(new DataSource("HEAD", "http://path/to/repo", DataSource.RepoKind.HG));
		DEFAULT_CLIENT_PREFERENCES.addProjectPreferences(pp);
	}
	/**
	 * Maps a short name (usually project id) to a preference.
	 */
	// Hashtable<String, ProjectPreferences> _projectPreferences = new Hashtable<String, ProjectPreferences>();
	Vector<ProjectPreferences> _projectPreferences = new Vector<ProjectPreferences>();

	/**
	 * Points to the user's scratch space. Directory must exist.
	 */
	private String _tempDirectory;

	/**
	 * Poits to the user's hg path.
	 */
	private String _hgPath;

	/**
	 * Private constructor to restrict usage.
	 */
	private ClientPreferences() {
		// disabled
	}

	/**
	 * Default constructor.
	 * 
	 * @param tempDirectory
	 * @param hgPath
	 */
	public ClientPreferences(String tempDirectory, String hgPath) {
		_tempDirectory = tempDirectory;
		_hgPath = hgPath;
	}

	/**
	 * Adds the preference to the project.
	 * 
	 * @param pref
	 *            Preference to add; the pref short name must be unique or an assertion will fail.
	 */
	public void addProjectPreferences(ProjectPreferences pref) {
		//String shortName = pref.getEnvironment().getShortName();

		for (ProjectPreferences pp : _projectPreferences) {
			if (pp.getEnvironment().getShortName().equals(pref.getEnvironment().getShortName())) {
				throw new RuntimeException("Duplicate project name: " + pp.getEnvironment().getShortName());
			}
		}

		_projectPreferences.add(pref);
	}
	
	/**
	 * Removes the preference from the project.
	 * 
	 * @param pref: preference to remove; if pref is not present, do nothing.  
	 */
	public void removeProjectPreferences(ProjectPreferences pref) {

		_projectPreferences.remove(pref);
	}
	
	/**
	 * Removes the preference from the project.
	 * 
	 * @param index: index of the preference to remove.  
	 */
	public void removeProjectPreferencesAtIndex(int index) {

		_projectPreferences.remove(index);
	}


	/**
	 * Returns the preferences.
	 * 
	 * @return
	 */
	public Collection<ProjectPreferences> getProjectPreference() {
		return _projectPreferences;
	}

	/**
	 * Get the preference for a given key.
	 * 
	 * @param shortName
	 * @return
	 */
	public ProjectPreferences getProjectPreferences(String shortName) {
		// assert _projectPreferences.containsKey(shortName);
		for (ProjectPreferences pp : _projectPreferences) {
			if (pp.getEnvironment().getShortName().equals(shortName)) {
				return pp;
			}
		}
		throw new RuntimeException("Project preferences: " + shortName + " does not exist.");
	}

	/**
	 * Load the saved preferences from config.xml.
	 * 
	 * TODO: Sensibly display when a preference is invalid.
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static ClientPreferences loadPreferencesFromXML() {
		ClientPreferences prefs = null;

		SAXBuilder builder = new SAXBuilder(false);
		Document doc = null;

		try {

			File configFile = new File(CONFIG_PATH);
			if (!configFile.exists()) {
				configFile.createNewFile();

				InputStream is = ClientPreferences.class.getResourceAsStream("defaultConfig.xml");
				assert is != null;

				OutputStream os = new FileOutputStream(configFile);
				assert os != null;

				byte[] buffer = new byte[1024];
				int len;

				while ((len = is.read(buffer)) >= 0)
					os.write(buffer, 0, len);

				is.close();
				os.close();

				_log.info("Created new configuration file: " + configFile.getAbsolutePath());

			} else {

				_log.info("Using existing config file: " + configFile.getAbsolutePath());

			}

			doc = builder.build(CONFIG_PATH);

			Element rootElement = doc.getRootElement();
			String tempDirectory = rootElement.getAttributeValue("tempDirectory");
			if (tempDirectory.startsWith(Constants.HOME)) {
				String firstPart = System.getProperty("user.home");
				String lastPart = tempDirectory.substring(Constants.HOME.length());
				tempDirectory = firstPart + lastPart;

				_log.trace("$HOME in temporary path: " + (firstPart + lastPart));
			}

			verifyPath(tempDirectory);

			String hgPath = rootElement.getAttributeValue("hgPath");
			verifyFile(hgPath);

			prefs = new ClientPreferences(tempDirectory, hgPath);

			List<Element> projectElements = rootElement.getChildren(IPrefXML.PROJECT);
			for (Element projectElement : projectElements) {
				String projectKind = projectElement.getAttributeValue(IPrefXML.KIND);
				String projectLabel = projectElement.getAttributeValue(IPrefXML.LABEL);
				String projectClone = projectElement.getAttributeValue(IPrefXML.CLONE);

				if (projectKind == null) {
					throw new RuntimeException("Kind attribute must be set for project element.");
				}
				if (projectLabel == null) {
					throw new RuntimeException("Label attribute must be set for project element.");
				}
				if (projectClone == null) {
					throw new RuntimeException("Clone attribute must be set for project element.");
				}

				if (projectClone.startsWith(Constants.HOME)) {
					String firstPart = System.getProperty("user.home");
					String lastPart = projectClone.substring(Constants.HOME.length());
					projectClone = firstPart + lastPart;

					_log.trace("$HOME in project path: " + (firstPart + lastPart));
				}

				// XXX: bring this back to validate the repositories
				// if (kind.equals(RepoKind.HG)) {
				// boolean isRepo = HgStateChecker.isHGRepository(hgPath, projectClone, tempDirectory);
				// if (!isRepo) {
				// throw new RuntimeException("Provided clone is not a valid Hg repository: " + projectClone);
				// }
				// }

				RepoKind kind = RepoKind.valueOf(projectKind);

				verifyPath(projectClone);

				if (kind == null || !kind.equals(RepoKind.HG)) {
					throw new RuntimeException("ClientPreferences - myKind not valid. (currently only HG is supported).");
				}

				if (projectLabel == null || projectLabel.equals("")) {
					throw new RuntimeException("ClientPreferences - myShortName must be specified.");
				}

				DataSource myEnvironment = new DataSource(projectLabel, projectClone, kind);

				_log.trace("Loaded project: " + myEnvironment);

				ProjectPreferences projectPreferences = new ProjectPreferences(myEnvironment, prefs);
				prefs.addProjectPreferences(projectPreferences);

				if (projectElement.getChild(IPrefXML.SOURCE) != null) {
					List<Element> sourceElements = projectElement.getChildren(IPrefXML.SOURCE);
					for (Element sourceElement : sourceElements) {
						String sourceLabel = sourceElement.getAttributeValue(IPrefXML.LABEL);
						String sourceClone = sourceElement.getAttributeValue(IPrefXML.CLONE);

						if (sourceLabel == null || sourceLabel.equals("")) {
							throw new RuntimeException("Label attribute must be set for source element.");
						}

						if (sourceClone == null || sourceClone.equals("")) {
							throw new RuntimeException("Clone attribute must be set for source element.");
						}

						if (sourceClone.startsWith(Constants.HOME)) {
							String firstPart = System.getProperty("user.home");
							String lastPart = sourceClone.substring(Constants.HOME.length());
							sourceClone = firstPart + lastPart;

							_log.trace("$HOME in project path: " + (firstPart + lastPart));
						}

						// XXX: bring this back to validate the repositories
						// if (kind.equals(RepoKind.HG)) {
						// boolean isRepo = HgStateChecker.isHGRepository(hgPath, sourceClone, tempDirectory);
						// if (!isRepo) {
						// throw new RuntimeException("Provided clone is not a valid Hg repository: " + sourceClone);
						// }
						// }

						DataSource source = new DataSource(sourceLabel, sourceClone, kind);
						_log.trace("Loaded data source: " + source);

						projectPreferences.addDataSource(source);
					}
				}
			}
		} catch (JDOMException jdome) {
			_log.error("Error parsing configuration file.", jdome);
		} catch (IOException ioe) {
			throw new RuntimeException("Error reading configuration file; " + ioe.getMessage(), ioe);
		} catch (Exception e) {
			throw new RuntimeException("Error parsing configuration file; " + e.getMessage(), e);
		}

		return prefs;
	}
	
	/**
	 * Save preferences to the default filename
	 * 
	 * @effect saves preferences to the default filename
	 */
	public static void savePreferencesToDefaultXML(ClientPreferences prefs) {
		savePreferencesToXML(prefs, CONFIG_PATH);
	}

	/**
	 * Save preferences to fName
	 * 
	 * @effect saves preferences to a file fName
	 */
	public static void savePreferencesToXML(ClientPreferences prefs, String fName) {

		Document doc = XMLTools.newXMLDocument();

		Element rootElem = new Element(IPrefXML.ROOT);
		rootElem.setAttribute(IPrefXML.TMP_DIR, prefs.getTempDirectory());
		rootElem.setAttribute(IPrefXML.HG_PATH, prefs.getHgPath());
		doc.setRootElement(rootElem);

		for (ProjectPreferences pp : prefs.getProjectPreference()) {
			Element projectElem = new Element(IPrefXML.PROJECT);
			projectElem.setAttribute(IPrefXML.KIND, pp.getEnvironment().getKind().name());
			projectElem.setAttribute(IPrefXML.LABEL, pp.getEnvironment().getShortName());
			projectElem.setAttribute(IPrefXML.CLONE, pp.getEnvironment().getCloneString());
			rootElem.addContent(projectElem);

			for (DataSource src : pp.getDataSources()) {
				Element sourceElem = new Element(IPrefXML.SOURCE);
				sourceElem.setAttribute(IPrefXML.LABEL, src.getShortName());
				sourceElem.setAttribute(IPrefXML.CLONE, src.getCloneString());
				sourceElem.setAttribute(IPrefXML.KIND, src.getKind().name());
				projectElem.addContent(sourceElem);
			}
		}

		XMLTools.writeXMLDocument(doc, fName);
	} 

	/**
	 * Check to ensure the provided file exists.
	 * 
	 * @param fName 
	 */
	private static void verifyFile(String fName) {

		// assert fName != null;
		// assert new File(fName).exists();
		// assert new File(fName).isFile();

		if (fName == null || !new File(fName).exists() || !new File(fName).isFile()) {
			throw new RuntimeException("File does not exist: " + fName);
		}
	}

	/**
	 * Check to ensure the provided path is a valid directory.
	 * 
	 * @param path
	 */
	private static void verifyPath(String path) {

		// assert path != null;
		// assert new File(path).exists();
		// assert new File(path).isDirectory();

		if (path == null || !new File(path).exists() || !new File(path).isDirectory()) {
			throw new RuntimeException("Path does not exist: " + path);
		}
	}

	/**
	 * 
	 * @return path to the user's hg binary
	 */
	public String getHgPath() {
		return _hgPath;
	}

	/**
	 * 
	 * @effect set the path to the user's hg binary
	 */
	public void setHgPath(String hgPath) {
		_hgPath = hgPath;
	}

	/**
	 * 
	 * @return path to the user's scratch space
	 */
	public String getTempDirectory() {
		return _tempDirectory;
	}

	/**
	 * 
	 * @effect set the path to the user's scratch space
	 */
	public void setTempDirectory(String tempDirectory) {
		_tempDirectory = tempDirectory;
	}
}
