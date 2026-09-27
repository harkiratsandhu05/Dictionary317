package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.io.InputStreamReader;
import java.io.IOException;
/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;

    private Socket socket;
    private BufferedReader br;
    private PrintWriter pw;

    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {
        try {
            socket = new Socket(host,port);
            br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            pw = new PrintWriter(socket.getOutputStream(), true);

            Status status = Status.readStatus(br);
            if (status.getStatusCode() != 220) {
                throw new DictConnectionException();
            } 
        } catch (Exception e) {
            close();
            throw new DictConnectionException(e);
        }
    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {
        try {
            if (pw != null) {
                pw.println("QUIT");
            }
            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {
            // Ignores exception as spec
        } finally {
            socket = null;
            br = null;
            pw = null;
        }
    }

    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();

        try {

            pw.println("DEFINE " + database.getName() + " \"" + word + "\"");
            Status status = Status.readStatus(br);

            if (status.getStatusCode() >= 500){
                return set;
            }
            if (status.getStatusCode() != 150) {
                throw new DictConnectionException();
            }

            while(true){
                Status s = Status.readStatus(br);

                if (s.getStatusCode() == 250) {
                    break;
                }
                if (s.getStatusCode() != 151) {
                    throw new DictConnectionException();
                }

                String[] atoms = DictStringParser.splitAtoms(s.getDetails());
                Definition def = new Definition(atoms[0], atoms[1]);

                String line;

                while ((line = br.readLine()) != null && !line.equals(".")) {
                    def.appendDefinition(line);
                }

                set.add(def);
            }
        } catch (IOException e) {
            throw new DictConnectionException(e);
        }

        return set;
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        try {
            pw.println("MATCH " + database.getName() + " " + strategy.getName() + " " + word);
            Status status = Status.readStatus(br);

            if (status.getStatusCode() >= 500){
                return set;
            }
            if (status.getStatusCode() != 152) {
                throw new DictConnectionException();
            }

            String line;
            while ((line = br.readLine()) != null && !line.equals(".")) {
                String[] atoms = DictStringParser.splitAtoms(line);
                set.add(atoms[1]);
            }

            Status s = Status.readStatus(br);
            if (s.getStatusCode() != 250) {
                throw new DictConnectionException();
            }

        } catch (IOException e) {
            throw new DictConnectionException(e);
        }

        return set;
    }

    /** Requests and retrieves a map of database name to an equivalent database object for all valid databases used in the server.
     *
     * @return A map of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();

        try {
            pw.println("SHOW DB");
            Status status = Status.readStatus(br);

            if (status.getStatusCode() >= 500){
                return databaseMap;
            }
            if (status.getStatusCode() != 110) {
                throw new DictConnectionException();
            }

            String line;
            while ((line = br.readLine()) != null && !line.equals(".")) {
                String[] atoms = DictStringParser.splitAtoms(line);
                databaseMap.put(atoms[0], new Database(atoms[0], atoms[1]));
            }

            Status s = Status.readStatus(br);
            if (s.getStatusCode() != 250) {
                throw new DictConnectionException();
            }

        } catch (IOException e) {
            throw new DictConnectionException(e);
        }

        return databaseMap;
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        try {
            pw.println("SHOW STRAT");
            Status status = Status.readStatus(br);

            if (status.getStatusCode() >= 500){
                return set;
            }
            if (status.getStatusCode() != 111) {
                throw new DictConnectionException();
            }

            String line;
            while ((line = br.readLine()) != null && !line.equals(".")) {
                String[] atoms = DictStringParser.splitAtoms(line);
                set.add(new MatchingStrategy(atoms[0], atoms[1]));
            }

            Status s = Status.readStatus(br);
            if (s.getStatusCode() != 250) {
                throw new DictConnectionException();
            }

        } catch (IOException e) {
            throw new DictConnectionException(e);
        }

        return set;
    }

    /** Requests and retrieves detailed information about the currently selected database.
     *
     * @return A string containing the information returned by the server in response to a "SHOW INFO <db>" command.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized String getDatabaseInfo(Database d) throws DictConnectionException {
	StringBuilder sb = new StringBuilder();

    try {
        pw.println("SHOW INFO " + d.getName());
        Status status = Status.readStatus(br);

        if (status.getStatusCode() >= 500){
            return sb.toString();
        }
        if (status.getStatusCode() != 112) {
            throw new DictConnectionException();
        }

        String line;
        while ((line = br.readLine()) != null && !line.equals(".")) {
            sb.append(line).append("\n");
        }

        Status s = Status.readStatus(br);
        if (s.getStatusCode() != 250) {
            throw new DictConnectionException();
        }

    } catch (IOException e) {
        throw new DictConnectionException(e);
    }

        return sb.toString();
    }
}
