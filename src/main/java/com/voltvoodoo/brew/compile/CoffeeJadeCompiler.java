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
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class CoffeeJadeCompiler implements Compiler {

    private final Scriptable globalScope;
    private final String options;

	public CoffeeJadeCompiler() {
        this("");
    }

	public CoffeeJadeCompiler(String options) {
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream("org/fusesource/coffeejade/coffeejade.js");
        try {
            try {
                Reader reader = new InputStreamReader(inputStream, "UTF-8");
                try {
                    Context context = Context.enter();
                    context.setOptimizationLevel(-1); // Without this, Rhino hits a 64K bytecode limit and fails
                    try {
                        globalScope = context.initStandardObjects();
                        context.evaluateReader(globalScope, reader, "coffeejade.js", 0, null);
                    } finally {
                        Context.exit();
                    }
                } finally {
                    reader.close();
                }
            } catch (UnsupportedEncodingException e) {
                throw new Error(e); // This should never happen
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            throw new Error(e); // This should never happen
        }

        this.options = options;
    }

	public String compile (String coffeeScriptSource) {
        Context context = Context.enter();
        try {
            Scriptable compileScope = context.newObject(globalScope);
            compileScope.setParentScope(globalScope);
            compileScope.put("coffeeScriptSource", compileScope, coffeeScriptSource);
            try {
                String source = String.format("CoffeeScript.compile(coffeeScriptSource, %s);", options);
                return (String)context.evaluateString(compileScope, source, "JCoffeeJadeCompiler", 0, null);
            } catch (JavaScriptException e) {
                throw new CoffeeScriptCompileException(e);
            }
        } finally {
            Context.exit();
        }
    }

    public void compile (File source, File target) throws CoffeeScriptCompileException, IOException {
        
        if ( target.exists() )
        {
            target.delete();
        }
        target.getParentFile().mkdirs();
        target.createNewFile();

        FileInputStream in = new FileInputStream( source );
        FileOutputStream out = new FileOutputStream( target );

        String compiled = compile( IOUtil.toString( in ) );
        IOUtil.copy( compiled, out );

        in.close();
        out.close();
    }

    public void compile(List<String> files, File sourceDir, File targetDir) {
        try {
            for(String path : files) {
                String newPath = path.substring(0, path.lastIndexOf('.')) + ".js";
                compile(new File(sourceDir, path), new File(targetDir, newPath));
            }
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }


}
