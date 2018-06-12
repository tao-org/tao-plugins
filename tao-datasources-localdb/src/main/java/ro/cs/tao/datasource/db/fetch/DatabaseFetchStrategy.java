/*
 * Copyright (C) 2017 CS ROMANIA
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package ro.cs.tao.datasource.db.fetch;

import org.apache.http.auth.UsernamePasswordCredentials;
import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.db.DatabaseSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.security.SessionStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;

public class DatabaseFetchStrategy implements ProductFetchStrategy {
    private final DatabaseSource source;

    public DatabaseFetchStrategy(DatabaseSource source) {
        this.source = source;
    }

    @Override
    public void addProperties(Properties properties) {
        //No-op
    }

    @Override
    public void setCredentials(UsernamePasswordCredentials credentials) {
        // no-op method
    }

    @Override
    public Path fetch(EOProduct product) throws InterruptedException {
        Path productPath = null;
        if (product != null && product.getLocation() != null) {
            productPath = Paths.get(product.getLocation());
            if (!productPath.isAbsolute()) {
                productPath = SessionStore.currentContext().getWorkspace().resolve(productPath);
                if (product.getEntryPoint() != null) {
                    productPath = productPath.resolve(product.getEntryPoint());
                }
            }
            if (Files.notExists(productPath)) {
                Logger.getLogger(DatabaseFetchStrategy.class.getName()).warning(String.format("Product '%s' not found",
                                                                                              product.getName()));
                productPath = null;
            }
        }
        return productPath;
    }

    @Override
    public DatabaseFetchStrategy clone() {
        return new DatabaseFetchStrategy(this.source);
    }
}
