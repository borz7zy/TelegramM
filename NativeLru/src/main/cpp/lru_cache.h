#ifndef TELEGRAM_M_LRU_CACHE_H
#define TELEGRAM_M_LRU_CACHE_H

#include <list>
#include <map>
#include <optional>

template<class Key, class Value>
class LRUCache{
public:
    typedef Key key_type;
    typedef Value value_type;
    typedef std::list<key_type> list_type;
    typedef std::map<key_type, std::pair<value_type, typename list_type::iterator>> map_type;

    LRUCache(size_t capacity) : mCapacity(capacity){}
    ~LRUCache(){}

    size_t size() const{return mMap.size();}
    size_t capacity() const{return mCapacity;}
    bool empty() const{return mMap.empty();}
    bool contains(const key_type& key){return mMap.find(key) != mMap.end();}

    void insert(const key_type& key, const value_type& value){
        typename map_type::iterator i = mMap.find(key);
        if(i == mMap.end()){
            if(size() >= mCapacity){
                evict();
            }

            mList.push_front(key);
            mMap[key] = std::make_pair(value, mList.begin());
        }
    }

    std::optional<value_type> get(const key_type& key){
        typename map_type::iterator i = mMap.find(key);
        if(i == mMap.end()){
            return std::nullopt;
        }

        typename list_type::iterator j = i->second.second;
        if(j != mList.begin()){
            mList.erase(j);
            mList.push_front(key);

            j = mList.begin();
            const value_type& value = i->second.first;
            mMap[key] = std::make_pair(value, j);

            return value;
        }else{
            return i->second.first;
        }
    }

    void clear(){
        mMap.clear();
        mList.clear();
    }

private:
    void evict(){
        typename list_type::iterator i = --mList.end();
        mMap.erase(*i);
        mList.erase(i);
    }

private:
    map_type mMap;
    list_type mList;
    size_t mCapacity;
};

#endif //TELEGRAM_M_LRU_CACHE_H
