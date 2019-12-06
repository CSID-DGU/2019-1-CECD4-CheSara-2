import os
import nltk 
import datetime
import subprocess
from operator import eq
from konlpy.tag import Twitter
from google.cloud import speech
from google.cloud import storage
from google.cloud.speech import enums
from google.cloud.speech import types

# function to apply current time 
def makeTimeData():
    from datetime import datetime
    now = datetime.now()
    yyyy = str(now.year)
    mo   = str(now.month).zfill(2)
    dd   = str(now.day).zfill(2)
    hh   = str(now.hour).zfill(2)
    mi   = str(now.minute).zfill(2)
    time_now = yyyy + mo + dd + hh + mi
    return time_now

#function to encode audio file(au) to wav
def encode_au_file(date):
    output = subprocess.Popen(['ffmpeg', '-i', 'input.au', '-ac', '1', '-ab',
     '12800', '-ar', '16000', date+'.wav', '-y'], shell=True, 
      stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    print("Encoding finished") 

#function to upload wav file about sniffing record to convert text
def upload_wav_to_google(date):
    from google.cloud import storage
    import os
    bucket_name = #'your bucket address except for "gs://"'
    file_name = date+'.wav'
    gClient = storage.Client.from_service_account_json(#"your google cloud service account.json") #json filename
    bucket = gClient.get_bucket(bucket_name)
    blob = bucket.blob(file_name)
    # Uploading from local file without open()	
    blob.upload_from_filename(file_name)  
    print('upload the file in cloud')

# function to convert audio file to text file using google cloud speech API
def transcribe_gcs(gcs_uri, wav_name):
    	
    client = speech.SpeechClient()
    audio = types.RecognitionAudio(uri=gcs_uri+"/"+wav_name)
    config = types.RecognitionConfig(
        encoding=enums.RecognitionConfig.AudioEncoding.LINEAR16,
        sample_rate_hertz=16000,
        language_code='ko-KR',
        model= "default",
        )
    operation = client.long_running_recognize(config, audio)
    response = operation.result()
    return response

def write_textfile(date,res) :
    with open(date+".txt", "w") as script:
        script.write("time record data\n")
        script.write("-----------------\n")
        for result in res.results:
            script.write(u'{}'.format(result.alternatives[0].transcript))
        script.write("\n")
        script.write("\nextract time data\n")
        script.write("-----------------\n")

# function to extract time data from text file using konlpy library
def extract_time_data(text_file_name) :
    #search week time data 
    def dic(search, Dic):
        for m in Dic:
            if(eq(search, m)):
                return True

    f = open(text_file_name+".txt", "r")
    data = f.read()
    #print(data)

    t = Twitter()

    morphs = t.morphs(data)
    ko = nltk.Text(morphs)

    # 시간
    timeDic = ['1시', '2시', '3시', '4시', '5시', '6시', '7시', '8시', '9시', '10시', '11시', '12시']
    timeCal = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
    timelist = []
    time = 0

    for i in morphs:
        if(eq(i[-1], "시")):
            timelist.append(i)
    #print(timelist)

    for i in range(len(timelist)):
        for j in range(len(timeDic)):
            if(eq(timelist[i], timeDic[j])):
                timeCal[j+1] += 1

    #print(timeCal)

    freMax = max(timeCal)
    for k in range(len(timeCal)):
        if(freMax == timeCal[k]):
            time = k

    #print(time)

    morphs = t.morphs(data)
    ko = nltk.Text(morphs)
    pos = t.pos(data, norm=True, stem=True) ## pos 나눠서 동사 찾기
    
    # time data
    daysDic = ['월요일', '화요일', '수요일', '목요일', '금요일', '토요일', '일요일']
    weekDic = ['이번주', '다음주', '다다음주', '이번', '다음']
    meetDic = ['만나다', '보다', '뵈다', '뵙다']
    dateDic = ['1일', '2일', '3일', '4일', '5일', '6일', '7일', '8일', '9일', '10일', '11일', '12일', '13일', '14일', '15일', '16일',
            '17일', '18일', '19일', '20일', '21일', '22일', '23일', '24일', '25일', '26일', '27일', '28일', '29일', '30일', '31일']
    # 어디서 만날지

    loc = ['OOO']
    
    for i in range(len(pos)):
        if(pos[i][0] == '에서'):
            loc.append(pos[i-1][0])

    location = loc[-1]
    #print(location)
    # 날짜(요일)

    now = datetime.datetime.now()
    dateoftoday = now.day
    today = datetime.datetime.today().weekday()

    # 날짜(일) 구하기 (요일로 말했을 때)
    date = 0
    days = []
    datesYo = ['~~~ O요일']
    daysCal = [0, 0, 0, 0, 0, 0, 0, 0]
    for i in range(len(pos)):
        for n in daysDic:
            if(eq(pos[i][0], n)):
                days.append(n)
                if(dic(pos[i-1][0], weekDic)):
                    datesYo.append(pos[i-1][0] + ' ' + n)
                if(dic(pos[i-2][0], weekDic)):
                    datesYo.append(pos[i-2][0] + ' ' + n)
        
    #print(days)
    for i in range(len(days)):
        for j in range(len(daysDic)):
            if(eq(days[i], daysDic[j])):
                daysCal[j+1] += 1

    freMaxDay = max(daysCal)
    for k in range(len(daysCal)):
        if(freMaxDay == daysCal[k]):
            dayInt = k
            
    day = daysDic[dayInt-1]
    #print(datesYo)
    #print(day)
    fulldate = datesYo[-1]
    #print(fulldate)
    indexDate = fulldate.find('다음')
    #print(indexDate)

    # 날짜차이 계산
    for i in range(len(daysDic)):
        if (eq(day, daysDic[i])):
            intDay = i + 1
    #print(intDay)

    #subDay = 0
    if (indexDate == -1):
        subDay = intDay - today - 1
        date = dateoftoday + subDay
    if (indexDate == 0):
        if (intDay < today):
            subDay = today - intDay
        else:
            subDay = intDay - today - 1
        date = dateoftoday + subDay + 7
        if (date >= 31):
            date -= 30
    #print(date)

    # 날짜(일) 구하기 (int일 로 말했을 때)
    dates = []
    dateCal = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
    for n, m in pos:
        if(eq(m, 'Number') & (eq(n[-1], '일'))):
            dates.append(n)
            
    for i in range(len(dates)):
        for j in range(len(dateDic)):
            if(eq(dates[i], dateDic[j])):
                dateCal[j+1] += 1

    #print("\n")

    freMaxDate = max(dateCal)
    
    if (freMaxDate != 0):
        for k in range(len(dateCal)):
            if(freMaxDate == dateCal[k]):
                date = k
    
    #print(date)
    # 오늘 , 내일
    totoDate = ['OO']
    for i in morphs:
        if(eq(i, "오늘") | eq(i, '내일')):
            totoDate.append(i)
            
    dateString = totoDate[-1]
    if (eq(dateString, '오늘')):
        date = now.day
    if (eq(dateString, '내일')):
        date = now.day + 1

    f.close()

    #add(write) time data to textfile
    f = open(text_file_name+".txt",'a')
    f.write('\n\n')
    f.write('장소 : ' + location + '에서' +'\n')
    f.write('시간 : ' + str(time) + '시' + '\n')
    f.write('날짜 : ' + str(date) + '일' + '\n')
    f.close()
    print("extarct time data")

# function to upload the text files to google cloud storage
def upload_txt_to_google(date):
    bucket_name = #'your bucket address except for "gs://"
    file_name = date+'.txt'
    gClient = storage.Client.from_service_account_json( #"your google cloud service account.json") #json filename
    bucket = gClient.get_bucket(bucket_name)
    blob = bucket.blob(file_name)
    # Uploading from local file without open()	
    blob.upload_from_filename(file_name)  
    print('upload the record')

# main
date = makeTimeData()
bucket_address = #"gs://your bucket address"
wav_file = date +".wav"
encode_au_file(date)
upload_wav_to_google(date)
response = transcribe_gcs(bucket_address, wav_file)
write_textfile(date, response)
extract_time_data(date)
upload_txt_to_google(date)
print("completed")

