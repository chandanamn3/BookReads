package io.bookreads.betterreadsdataloader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;



import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
/*import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;*/
import org.json.*;



import io.bookreads.betterreadsdataloader.author.Author;
import io.bookreads.betterreadsdataloader.author.AuthorRepository;
import io.bookreads.betterreadsdataloader.book.Book;
import io.bookreads.betterreadsdataloader.book.BookRepository;
import io.bookreads.betterreadsdataloader.connection.DataStaxAstraProperties;


@SpringBootApplication
@EnableConfigurationProperties(DataStaxAstraProperties.class)
/*@EnableCassandraRepositories("io.bookreads.betterreadsdataloader")*/
public class BetterreadsDataLoaderApplication {
    @Value("${datadump.location.author}")
	private String authorDumpLocation;
	@Value("${datadump.location.works}")
	private String worksDumpLocation;
	@Autowired 
	AuthorRepository authorRepository;
	@Autowired 
	BookRepository bookRepository;
	public static void main(String[] args) {
		SpringApplication.run(BetterreadsDataLoaderApplication.class, args);
	}
	@Bean
    public CqlSessionBuilderCustomizer sessionBuilderCustomizer(DataStaxAstraProperties astraProperties) {
        Path bundle = astraProperties.getSecureConnectBundle().toPath();
        return builder -> builder.withCloudSecureConnectBundle(bundle);
    }
	private void initAuthors()
	{
		Path path = Paths.get(authorDumpLocation);
		try( Stream<String> lines= Files.lines(path)){
			lines.forEach(line->{
				String jsonString =line.substring(line.indexOf("{"));
				try{
				JSONObject jsonObject= new JSONObject(jsonString);
				Author author = new Author();
				author.setName(jsonObject.optString("name"));
				author.setPersonalName(jsonObject.optString("personal_name"));
				author.setId(jsonObject.optString("key").replace("/authors/",""));
				System.out.println("Saving Author"+author.getName()+"...");

				authorRepository.save(author);
				}
				catch(JSONException ex)
				{
					ex.printStackTrace();
				}

			});

		}catch(IOException e){
			e.printStackTrace();
		 }
	}
	private void initWorks(){
		Path path = Paths.get(worksDumpLocation);
		DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
		try( Stream<String> lines= Files.lines(path)){
			lines.forEach(line->{
				String jsonString =line.substring(line.indexOf("{"));
				try{
				JSONObject jsonObject= new JSONObject(jsonString);
				Book book = new Book();
				book.setId(jsonObject.getString("key").replace("/works/", ""));
				book.setName(jsonObject.optString("title"));
				JSONObject descriptionObject =jsonObject.optJSONObject("description");
				if(descriptionObject != null){
					book.setDescription(descriptionObject.optString("value"));
				}

				JSONObject publishedObj = jsonObject.optJSONObject("created");
				if(publishedObj != null){
					String dateStr = publishedObj.getString("value");
					book.setPublishedDate(LocalDate.parse(dateStr,dateFormat));

				}
				JSONArray coversJSONArray  =jsonObject.optJSONArray("covers");
				if(coversJSONArray != null){
					List<String> coverIds = new ArrayList<>();
					for(int i=0;i<coversJSONArray.length();i++){
						coverIds.add(coversJSONArray.getString(i));
					}
					book.setCoverIds(coverIds);
				}
				
				JSONArray authorsJSONArray  = jsonObject.optJSONArray("authors");
				 
				if(authorsJSONArray != null){
					List<String> authorIds = new ArrayList<>();
					for(int i=0;i<authorsJSONArray.length();i++){
						String authorId =authorsJSONArray.getJSONObject(i).getJSONObject("author").getString("key")
						.replace("/authors/","");
						authorIds.add(authorId);
					}
					book.setAuthorIds(authorIds);
					List<String> authorNames = authorIds.stream().map(id -> authorRepository.findById(id))
					.map(OptionalAuthor ->{
                         if(!OptionalAuthor.isPresent()) return "UnknownAuthor";
						 return OptionalAuthor.get().getName();
					}).collect(Collectors.toList());
					book.setAuthorNames(authorNames);
					
				}

				System.out.println("Saving Book"+book.getName()+"...");
				bookRepository.save(book);
				
				
				
				}
				catch(JSONException ex)
				{
					ex.printStackTrace();
				}
		});
	
	}catch(IOException e){
			e.printStackTrace();
		 }
	}

    @PostConstruct
	public void start()
	{
       // initAuthors();
		initWorks();
	}

}
