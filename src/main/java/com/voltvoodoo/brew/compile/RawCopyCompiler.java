package com.voltvoodoo.brew.compile;

import org.codehaus.plexus.util.IOUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class RawCopyCompiler implements Compiler
{
    private final CompilerMojo mojo;

    public RawCopyCompiler(CompilerMojo mojo)
    {
        this.mojo = mojo;
    }

    public void compile(List<String> files, File sourceDir, File targetDir)
    {
        for (String path : files)
        {
            try
            {
                copyFile(path, sourceDir, targetDir);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public void copyFile(String path, File sourceDir, File targetDir) throws IOException
    {
        FileOutputStream out = null;
        FileInputStream in = null;
        try
        {
            File source = new File(sourceDir, path);
            if (source.exists() && !mojo.isSourceFile(source))
            {
                File target = new File(targetDir, path);
                File parentDir = target.getParentFile();
                parentDir.mkdirs();
                if (parentDir.exists())
                {
                    in = new FileInputStream(source);
                    out = new FileOutputStream(target);
                    IOUtil.copy(in, out);
                }
            }
        }
        finally
        {
            IOUtil.close(in);
            IOUtil.close(out);
        }
    }

}
