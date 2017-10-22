package edu.shu.transclient;

import edu.shu.util.SystemInfo;
import edu.shu.dao.FilmDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TransclientApplicationTests {
	@Autowired
	FilmDao filmDao;

	@Test
	public void contextLoads() {
//		Film film = new Film();
//		film.setCreateTime(new Date());
//		film.setFilmId("111");
//		film.setState(0);
//		film.setUserName("135_1504859384362");
//		film.setPassWord("mt42L5KjMUiHo148");
//		film.setRemoteIp("127.0.0.1");

		System.out.println(SystemInfo.getMemoryInfo());
	}

}
