/*
 * Copyright 2010 David Yeung
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.voltvoodoo.brew.compile;

import org.codehaus.plexus.util.IOUtil;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Scriptable;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class CoffeeJadeCompiler implements Compiler
{
    private final CompilerMojo mojo;
    private final Scriptable globalScope;
    private final String options;
    private File singleViewFile;
    private Map<String, String> viewCache = new HashMap<String, String>();

    public CoffeeJadeCompiler(CompilerMojo mojo, String options)
    {
        this.mojo = mojo;
        this.singleViewFile = mojo.getViewsMapOutputFile();
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream("org/fusesource/coffeejade/coffeejade.js");
        try
        {
            try
            {
                Reader reader = new InputStreamReader(inputStream, "UTF-8");
                try
                {
                    Context context = Context.enter();
                    context.setOptimizationLevel(-1); // Without this, Rhino hits a 64K bytecode limit and fails
                    try
                    {
                        globalScope = context.initStandardObjects();
                        context.evaluateString(globalScope, "var window = {};", "JCoffeeJadeCompiler", 0, null);
                        context.evaluateReader(globalScope, reader, "coffeejade.js", 0, null);
                    }
                    finally
                    {
                        Context.exit();
                    }
                }
                finally
                {
                    reader.close();
                }
            }
            catch (UnsupportedEncodingException e)
            {
                throw new Error(e); // This should never happen
            }
            finally
            {
                inputStream.close();
            }
        }
        catch (IOException e)
        {
            throw new Error(e); // This should never happen
        }

        this.options = options;
    }

    public void writeSingleViewFile()
    {
        if (isSingleViewFileEnabled())
        {
            singleViewFile.getParentFile().mkdirs();
            PrintWriter out = null;
            try
            {
                out = new PrintWriter(new FileWriter(singleViewFile));
                out.println("define(['frameworks'], function() {");
                out.println("  var templates;");
                out.println("  templates = {};");

                for (Map.Entry<String, String> entry : viewCache.entrySet())
                {
                    String jadeSource = entry.getKey();
                    String js = entry.getValue();
                    // lets lose the first line...
                    int i = js.indexOf('\n');
                    js = js.substring(i + 1);
                    out.println("  templates['" + jadeSource + "'] = (function(locals) {");
                    out.println(js);
                    out.println("  });");
                }
                out.println("  return templates;");
                out.println("});");
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            finally
            {
                if (out != null)
                {
                    try
                    {
                        out.close();
                    }
                    catch (Exception e)
                    {
                        // ignore
                    }
                }
            }
        }
    }

    public String compile(String jadeSource, String relativePath)
    {
        Context context = Context.enter();
        try
        {
            Scriptable compileScope = context.newObject(globalScope);
            compileScope.setParentScope(globalScope);
            compileScope.put("jadeSource", compileScope, jadeSource);
            try
            {
                String source = String.format("window.jade.compile(jadeSource, %s).code;", options);
                String js = (String) context.evaluateString(compileScope, source, "JCoffeeJadeCompiler", 0, null);
                cacheResult(relativePath, js);
                return js;
            }
            catch (JavaScriptException e)
            {
                throw new CoffeeScriptCompileException(e);
            }
        }
        finally
        {
            Context.exit();
        }
    }

    private void cacheResult(String source, String js)
    {
        if (isSingleViewFileEnabled())
        {
            viewCache.put(source, js);
        }

    }

    protected boolean isSingleViewFileEnabled()
    {
        return singleViewFile != null;
    }

    public void compile(File source, File target) throws CoffeeScriptCompileException, IOException
    {
        if (target.exists())
        {
            target.delete();
        }
        target.getParentFile().mkdirs();
        target.createNewFile();

        FileInputStream in = new FileInputStream(source);
        FileOutputStream out = new FileOutputStream(target);

        String compiled = compile(IOUtil.toString(in), source.toString());
        IOUtil.copy(compiled, out);

        in.close();
        out.close();
    }

    public void compile(List<String> files, File sourceDir, File targetDir)
    {
        try
        {
            for (String path : files)
            {
                String newPath = path.substring(0, path.lastIndexOf('.')) + ".js";
                compile(new File(sourceDir, path), new File(targetDir, newPath));
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }


    {
    }
}
