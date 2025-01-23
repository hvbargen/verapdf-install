package org.eclipse.birt.test.verapdf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Main {

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
		String installerDirName = tempDir + java.io.File.separator + "veraPDFInstaller";
		File installerDir = new File(installerDirName);
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
		
		String installDir = tempDir + File.separator + "verapdf";
		
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

}
