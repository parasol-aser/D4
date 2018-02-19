package edu.tamu.aser.tide.plugin.handlers;

import org.eclipse.core.resources.IFile;

public class PrintableString {
    private String fileName;
    private String lineNum;
    private IFile file;
    
    public PrintableString(String name, String line, IFile f) {
        fileName = name;
        lineNum = line;
        file = f;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PrintableString) {
            PrintableString ps2 = (PrintableString)o;
            String s1 = fileName + lineNum;
            String s2 = ps2.fileName + ps2.lineNum;
            return s1.equals(s2);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (fileName + lineNum).hashCode();
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public String getLineNum() {
        return lineNum;
    }
    
    public IFile getFile() {
        return file;
    }
    
}
