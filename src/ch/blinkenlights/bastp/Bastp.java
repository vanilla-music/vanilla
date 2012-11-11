package ch.blinkenlights.bastp;

import ch.blinkenlights.bastp.OggFile;
import ch.blinkenlights.bastp.FlacFile;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.Hashtable;


public class Bastp {
	
	public Bastp() {
	}
	
	public Hashtable getTags(String fname) {
		Hashtable tags = new Hashtable();
		try {
			RandomAccessFile ra = new RandomAccessFile(fname, "r");
			tags = getTags(ra);
			ra.close();
		}
		catch(Exception e) {
			/* we dont' care much: SOMETHING went wrong. d'oh! */
		}
		
		return tags;
	}
	
	public Hashtable getTags(RandomAccessFile s) {
		Hashtable tags = new Hashtable();
		byte[] file_ff = new byte[4];
		
		try {
			s.read(file_ff);
			String magic = new String(file_ff);
			if(magic.equals("fLaC")) {
				tags = (new FlacFile()).getTags(s);
			}
			else if(magic.equals("OggS")) {
				tags = (new OggFile()).getTags(s);
			}
			tags.put("_magic", magic);
		}
		catch (IOException e) {
		}
		return tags;
	}
	
}

