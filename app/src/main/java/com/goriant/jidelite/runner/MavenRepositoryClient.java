package com.goriant.jidelite.runner;

import java.io.File;
import java.io.IOException;
import java.util.List;

interface MavenRepositoryClient {

    File fetchPom(MavenCoordinate coordinate, List<String> repositories) throws IOException;

    File fetchJar(MavenCoordinate coordinate, List<String> repositories) throws IOException;
}
