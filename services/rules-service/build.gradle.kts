plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.hibernate.orm") version "7.2.12.Final"
	id("org.graalvm.buildtools.native") version "0.11.5"
}

group = "com.research"
version = "0.0.1-SNAPSHOT"
description = "rules-service"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-kafka")

	implementation("com.fasterxml.jackson.core:jackson-databind")
	implementation("com.fasterxml.jackson.core:jackson-core")
	implementation("com.fasterxml.jackson.core:jackson-annotations")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

	compileOnly("org.projectlombok:lombok:1.18.34")
	annotationProcessor("org.projectlombok:lombok:1.18.34")
	annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	// Add Mockito for unit testing
	testImplementation("org.mockito:mockito-junit-jupiter:5.5.0")
	implementation("org.postgresql:postgresql")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

hibernate {
	enhancement {
		enableAssociationManagement = false
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
