package org.eclipse.birt.test.verapdf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class Main {


	public static void initializeVeraPDF(Context cx, Scriptable scope) {
		var js  = "importPackage(org.verapdf.gf.foundry);\n"
				+ "VeraGreenfieldFoundryProvider.initialise();\n";
        Object result = cx.evaluateString(scope, js, "<verapdf_init>", 1, null);
	}
      
	
	/**
	 * @param pdf_fname
	 * @param flavourName
	 */
	public static void checkFlavour(Context cx, Scriptable scope, String pdf_fname, String flavourName) {
		var js  = "importPackage(org.verapdf.pdfa);\n"
				+ "importPackage(org.verapdf.pdfa.flavours);\n"
				+ "\n"
				+ "var flavour = PDFAFlavour.fromString(flavourName);\n"
				+ "\n"
				+ "var parser = Foundries.defaultInstance().createParser(new java.io.FileInputStream(pdf_fname), flavour);\n"
				+ "var validator = Foundries.defaultInstance().createValidator(flavour, false);\n"
				+ "var result = validator.validate(parser);\n"
				+ "if (result.isCompliant()) {\n"
				+ "    out.println(pdf_fname + \": File is a valid PDF/A \" + flavourName);\n"
				+ "} else {\n"
				+ "	   // Show the errors\n"
				+ "	   err.println(pdf_fname + \": File is not a valid PDF/A \" + flavourName);\n"
				+ "    err.println(\"Validation errors:\");\n"
				+ "    var failedChecks = result.getFailedChecks();\n"
				+ "    var keysArray = new java.util.ArrayList(failedChecks.keySet());\n"
				+ "    // java.util.Collections.sort(keysArray, (o1, o2) -> o1.getSpecification().compareTo(o2.getSpecification()));\n"
				+ "    var keys = [];\n"
				+ "    for (var i=0; i<keysArray.size(); i++) { keys.push(keysArray[i]); }\n"
				+ "	   for (var i=0; i<keys.length; i++) {\n"
				+ "        var rule = keys[i];\n"
				+ "	       err.println(rule.toString() + \": \" + String.valueOf(failedChecks.get(rule)));\n"
				+ "	       err.println(rule.getSpecification().getName());\n"
				+ "	       err.println(rule.getSpecification().getDescription());\n"
				+ "	   }\n"
				+ "}\n";
        ScriptableObject.putProperty(scope, "pdf_fname", Context.javaToJS(pdf_fname, scope));
        ScriptableObject.putProperty(scope, "flavourName", Context.javaToJS(flavourName, scope));
        ScriptableObject.putProperty(scope, "out", Context.javaToJS(System.out, scope));
        ScriptableObject.putProperty(scope, "err", Context.javaToJS(System.err, scope));
        Object result = cx.evaluateString(scope, js, "<checkFlavour>", 1, null);        
	}


	public static void main(String[] args) throws Exception {
		
		byte[] buffer = new byte[32768];

		String tempDir = System.getenv("TMP");
		if (tempDir == null) {
			tempDir = System.getenv("TEMP");
		}
		if (tempDir == null) {
			System.err.println("TMP/TEMP environment variable must be set.");
			System.exit(1);
		}
		final String installerDirName = tempDir + java.io.File.separator + "veraPDFInstaller";
		File installerDir = new File(installerDirName);
		final String installDir = tempDir + File.separator + "verapdf";
		
		if (!new File(installDir).isDirectory()) {
		
			if (!installerDir.mkdir()) {
				System.err.println("Could not create directory " + installerDirName);
				System.exit(1);
			}
			System.out.println("Extracting archive into directory " + installerDirName);
	
			File jarFile = null;
	
			URL installerZip = Main.class.getClassLoader().getResource("verapdf-installer.zip");
			try (ZipFile installerZipFile = new ZipFile(installerZip.getFile())) {
				for (var ei = installerZipFile.entries(); ei.hasMoreElements(); ) {
					ZipEntry entry = ei.nextElement();
					String[] entry_path = entry.getName().split("/");
					// Remove the version-dependent first part of the path.
					String[] new_entry_path = Arrays.copyOfRange(entry_path, 1, entry_path.length);
					if (new_entry_path.length == 0) {
						// The entry for the base path, which we want to ignore
						continue;
					}
					File destination = new File(installerDir, String.join(File.separator, new_entry_path));
					if (entry.isDirectory()) {
						destination.mkdirs();
					} else {
						if (destination.getName().endsWith(".jar") && destination.getName().startsWith("verapdf-izpack-installer-")) {
							jarFile = destination;
						}
						try (FileOutputStream fos = new FileOutputStream(destination);
								InputStream fis = installerZipFile.getInputStream(entry)	) {
							int len;
					        while ((len = fis.read(buffer)) > 0) {
					            fos.write(buffer, 0, len);
					        }
						}
					}
				}
			}
			if (jarFile == null) {
				System.err.println("Could not find a verapdf installer JAR file in the archive.");
				System.exit(1);
			}
		
			String autoInstallXml = "<AutomatedInstallation langpack=\"eng\">\r\n"
					+ "    <com.izforge.izpack.panels.htmlhello.HTMLHelloPanel id=\"welcome\"/>\r\n"
					+ "    <com.izforge.izpack.panels.target.TargetPanel id=\"install_dir\">\r\n"
					+ "        <installpath>" + installDir.replace(File.separatorChar, '/') + "</installpath>\r\n"
					+ "    </com.izforge.izpack.panels.target.TargetPanel>\r\n"
					+ "    <com.izforge.izpack.panels.packs.PacksPanel id=\"sdk_pack_select\">\r\n"
					+ "        <pack index=\"0\" name=\"veraPDF GUI\" selected=\"false\"/>\r\n"
					+ "        <pack index=\"1\" name=\"veraPDF Mac and *nix Scripts\" selected=\"true\"/>\r\n"
					+ "        <pack index=\"2\" name=\"veraPDF Validation model\" selected=\"true\"/>\r\n"
					+ "        <pack index=\"3\" name=\"veraPDF Documentation\" selected=\"true\"/>\r\n"
					+ "        <pack index=\"4\" name=\"veraPDF Sample Plugins\" selected=\"false\"/>\r\n"
					+ "    </com.izforge.izpack.panels.packs.PacksPanel>\r\n"
					+ "    <com.izforge.izpack.panels.install.InstallPanel id=\"install\"/>\r\n"
					+ "    <com.izforge.izpack.panels.finish.FinishPanel id=\"finish\"/>\r\n"
					+ "</AutomatedInstallation>";
			File autoXmlFile = new File(tempDir + File.separator + "verapdf-auto-install.xml");
			try (var fh = new FileWriter(autoXmlFile)) {
				fh.write(autoInstallXml);
			}
			
			File javaHomeBin = new File(new File(System.getProperty("java.home")), "bin");
			File javaExe = new File(javaHomeBin, "java");
			if (!javaExe.exists()) {
				javaExe = new File(javaHomeBin, "java.exe");
			}
			if (!javaExe.exists()) {
				System.err.println("Could not determine java executable.");
				System.exit(2);
			}
			ProcessBuilder pb = new ProcessBuilder(javaExe.getAbsolutePath(), "-jar", jarFile.getAbsolutePath(), autoXmlFile.getAbsolutePath() );
	        pb.inheritIO();
	        System.out.println("Running installer...");
	        System.out.flush();
	        Process installerProcess = pb.start();
	        int exitCode = installerProcess.waitFor();
	        if (exitCode != 0) {
	        	System.exit(exitCode);
	        }
	        System.out.println("Installation succeeded.");
		}
		
        // Create a new class loader and
        // add the extracted JAR file to the class path.
        File baseDir = new File(installDir);
        File binDir = new File(baseDir, "bin");
        File jar = new File(binDir, "greenfield-apps-1.26.5.jar");
        URLClassLoader child = new URLClassLoader(
                new URL[] {jar.toURI().toURL()},
                Main.class.getClassLoader()
        );

		System.setProperty("file.encoding", "UTF-8");
		System.setProperty("app.name", "VeraPDF validation GUI");
		System.setProperty("app.repo", binDir.getAbsolutePath());
		System.setProperty("app.home", baseDir.getAbsolutePath());
		System.setProperty("basedir", baseDir.getAbsolutePath());
        
        // Initialize Javascript with this class loader
        try (Context cx = Context.enter()) {
	        cx.setApplicationClassLoader(child);
	        
		        // This would run as individual tests
			String pdf_fname = "C:/Users/Henning/Downloads/HyperlinkLabel.pdf";
			System.out.println("Start.");

	        Scriptable scope = new ImporterTopLevel(cx);
	        initializeVeraPDF(cx, scope);

	        String[] testfiles = new String[] { pdf_fname };
	        for (var pdfFile: testfiles) {
				checkFlavour(cx, scope, pdfFile, "3b");
				checkFlavour(cx, scope, pdfFile, "UA1");
	        }
			System.out.println("Done.");
        }
	}

}
